/*
 * Copyright 2021 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.dom

import cats.Foldable
import cats.data.OptionT
import cats.effect.kernel.Async
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Mutex
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import org.http4s.Method
import org.http4s.client.websocket.WSClientHighLevel
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.MessageEvent
import org.scalajs.dom.WebSocket
import org.typelevel.ci._
import scodec.bits.ByteVector

import java.io.IOException
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object WebSocketClient {

  def apply[F[_]](implicit F: Async[F]): WSClientHighLevel[F] = new WSClientHighLevel[F] {
    def connectHighLevel(request: WSRequest): Resource[F, WSConnectionHighLevel[F]] =
      for {
        dispatcher <- Dispatcher.sequential[F]
        messages <- Queue.unbounded[F, Option[MessageEvent]].toResource
        mutex <- Mutex[F].toResource
        close <- F.deferred[CloseEvent].toResource
        ws <- Resource.makeCaseFull[F, WebSocket] { poll =>
          poll {
            F.async[WebSocket] { cb =>
              F.delay {
                if (request.method != Method.GET)
                  cb(Left(new IllegalArgumentException("Must be GET Request")))

                val protocols = request
                  .headers
                  .get(ci"Sec-WebSocket-Protocol")
                  .toList
                  .flatMap(_.toList.map(_.value))

                val ws = new WebSocket(request.uri.renderString, protocols.toJSArray)
                ws.binaryType = "arraybuffer" // the default is blob

                ws.onopen = { _ =>
                  ws.onmessage = e => // setup message handler
                    dispatcher.unsafeRunAndForget(messages.offer(Some(e)))

                  ws.onclose = e => // replace the close handler
                    dispatcher.unsafeRunAndForget(messages.offer(None) *> close.complete(e))

                  // no explicit error handler. according to spec:
                  //   1. an error event is *always* followed by a close event and
                  //   2. an error event doesn't carry any useful information *by design*

                  cb(Right(ws))
                }

                // a close at this stage can only be an error
                // following spec we cannot get any detail about the error
                // https://websockets.spec.whatwg.org/#eventdef-websocket-error
                ws.onclose = _ => cb(Left(new IOException("Connection failed")))

                Some(F.delay(ws.close()))
              }
            }
          }
        } {
          case (ws, exitCase) =>
            val reason = exitCase match {
              case Resource.ExitCase.Succeeded =>
                None
              case Resource.ExitCase.Errored(ex) =>
                val reason = ex.toString
                // reason must be no longer than 123 bytes of UTF-8 text
                // UTF-8 character is max 4 bytes so we can fast-path
                if (reason.length <= 30 || reason.getBytes.length <= 123)
                  Some(reason)
                else
                  None
              case Resource.ExitCase.Canceled =>
                Some("canceled")
            }

            val shutdown = F
              .async_[CloseEvent] { cb =>
                ws.onerror = e => cb(Left(js.JavaScriptException(e)))
                ws.onclose = e => cb(Right(e))
                reason match { // 1000 "normal closure" is only code supported in browser
                  case Some(reason) => ws.close(1000, reason)
                  case None => ws.close(1000)
                }
              }
              .flatMap(close.complete(_)) *> messages.offer(None)

            F.delay(ws.readyState).flatMap {
              case WebSocket.CONNECTING | WebSocket.OPEN => shutdown
              case WebSocket.CLOSING => close.get.void
              case WebSocket.CLOSED => F.unit
              case s => F.raiseError(new IllegalStateException(s"WebSocket.readyState: $s"))
            }
        }
      } yield new WSConnectionHighLevel[F] {

        def closeFrame: DeferredSource[F, WSFrame.Close] =
          (close: DeferredSource[F, CloseEvent]).map(e => WSFrame.Close(e.code, e.reason))

        def receive: F[Option[WSDataFrame]] =
          close
            .tryGet
            .map(_.isDefined)
            .ifM(
              OptionT(messages.tryTake.map(_.flatten)).map(decodeMessage).value,
              OptionT(mutex.lock.surround(messages.take)).map(decodeMessage).value
            )

        override def receiveStream: Stream[F, WSDataFrame] =
          Stream
            .eval(close.tryGet.map(_.isDefined))
            .ifM(
              Stream.evalUnChunk(
                messages.tryTakeN(None).map(m => Chunk.from(m.flatMap(_.toList)))),
              Stream.resource(mutex.lock) >> Stream.fromQueueNoneTerminated(messages)
            )
            .map(decodeMessage)

        private def decodeMessage(e: MessageEvent): WSDataFrame =
          e.data match {
            case s: String => WSFrame.Text(s)
            case b: js.typedarray.ArrayBuffer =>
              WSFrame.Binary(ByteVector.fromJSArrayBuffer(b))
            case _ => // this should never happen
              throw new AssertionError
          }

        override def sendText(text: String): F[Unit] =
          F.delay(ws.send(text))

        override def sendBinary(bytes: ByteVector): F[Unit] =
          F.delay(ws.send(bytes.toJSArrayBuffer))

        def send(wsf: WSDataFrame): F[Unit] =
          wsf match {
            case WSFrame.Text(data, true) => sendText(data)
            case WSFrame.Binary(data, true) => sendBinary(data)
            case _ =>
              F.raiseError(new IllegalArgumentException("DataFrames cannot be fragmented"))
          }

        def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit] =
          wsfs.foldMapM(send(_))

        def subprotocol: Option[String] = Option(ws.protocol).filter(_.nonEmpty)
      }
  }

}
