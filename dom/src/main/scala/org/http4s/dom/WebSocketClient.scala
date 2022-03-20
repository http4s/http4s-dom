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
import cats.effect.std.Queue
import cats.effect.std.Semaphore
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.INothing
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

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

final class WebSocketException private[dom] (
    private[dom] val reason: String
) extends RuntimeException(reason)

object WebSocketClient {

  def apply[F[_]](implicit F: Async[F]): WSClientHighLevel[F] = new WSClientHighLevel[F] {
    def connectHighLevel(request: WSRequest): Resource[F, WSConnectionHighLevel[F]] =
      for {
        dispatcher <- Dispatcher[F]
        messages <- Queue.unbounded[F, Option[MessageEvent]].toResource
        semaphore <- Semaphore[F](1).toResource
        error <- F.deferred[Either[Throwable, INothing]].toResource
        close <- F.deferred[CloseEvent].toResource
        ws <- Resource.makeCase {
          F.async_[WebSocket] { cb =>
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
              ws.onerror = // replace the error handler
                e =>
                  dispatcher.unsafeRunAndForget(error.complete(Left(js.JavaScriptException(e))))
              cb(Right(ws))
            }

            ws.onerror = e => cb(Left(js.JavaScriptException(e)))
            ws.onmessage = e => dispatcher.unsafeRunAndForget(messages.offer(Some(e)))
            ws.onclose =
              e => dispatcher.unsafeRunAndForget(messages.offer(None) *> close.complete(e))
          }
        } {
          case (ws, exitCase) =>
            val reason = exitCase match {
              case Resource.ExitCase.Succeeded =>
                None
              case Resource.ExitCase.Errored(ex: WebSocketException) =>
                Some(ex.reason)
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
              case 0 | 1 => shutdown // CONNECTING | OPEN
              case 2 => close.get.void // CLOSING
              case 3 => F.unit // CLOSED
              case s => F.raiseError(new IllegalStateException(s"WebSocket.readyState: $s"))
            }
        }
      } yield new WSConnectionHighLevel[F] {

        def closeFrame: DeferredSource[F, WSFrame.Close] =
          (close: DeferredSource[F, CloseEvent]).map(e => WSFrame.Close(e.code, e.reason))

        def receive: F[Option[WSDataFrame]] = semaphore
          .permit
          .use(_ => OptionT(messages.take).semiflatMap(decodeMessage).value)
          .race(error.get.rethrow)
          .map(_.merge)

        override def receiveStream: Stream[F, WSDataFrame] =
          Stream
            .resource(semaphore.permit)
            .flatMap(_ => Stream.fromQueueNoneTerminated(messages))
            .evalMap(decodeMessage)
            .concurrently(Stream.exec(error.get.rethrow.widen))

        private def decodeMessage(e: MessageEvent): F[WSDataFrame] =
          e.data match {
            case s: String => WSFrame.Text(s).pure.widen[WSDataFrame]
            case b: js.typedarray.ArrayBuffer =>
              WSFrame.Binary(ByteVector.fromJSArrayBuffer(b)).pure.widen[WSDataFrame]
            case _ =>
              F.raiseError[WSDataFrame](
                new WebSocketException(s"Unsupported data: ${js.typeOf(e.data)}")
              )
          }

        override def sendText(text: String): F[Unit] =
          errorOr(F.delay(ws.send(text)))

        override def sendBinary(bytes: ByteVector): F[Unit] =
          errorOr(F.delay(ws.send(bytes.toJSArrayBuffer)))

        def send(wsf: WSDataFrame): F[Unit] =
          wsf match {
            case WSFrame.Text(data, true) => sendText(data)
            case WSFrame.Binary(data, true) => sendBinary(data)
            case _ =>
              F.raiseError(new IllegalArgumentException("DataFrames cannot be fragmented"))
          }

        private def errorOr(fu: F[Unit]): F[Unit] = error.tryGet.flatMap {
          case Some(error) => F.fromEither[Unit](error)
          case None => fu
        }

        def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit] =
          wsfs.foldMapM(send(_))

        def subprotocol: Option[String] = Option(ws.protocol).filter(_.nonEmpty)
      }
  }

}
