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
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.DeferredSink
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.INothing
import fs2.Stream
import fs2.concurrent.Channel
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.MessageEvent
import org.scalajs.dom.WebSocket
import scodec.bits.ByteVector

import scala.scalajs.js
import org.http4s.Method

final class WSException private[dom] (
    private[dom] val code: Int,
    private[dom] val reason: String
) extends RuntimeException(reason)

object WSClient {

  def apply[F[_]](implicit F: Async[F]): WSClient[F] = new WSClient[F] {
    def connectHighLevel(request: WSRequest): Resource[F, WSConnection[F]] =
      for {
        dispatcher <- Dispatcher[F]
        messages <- Resource.make(Channel.unbounded[F, MessageEvent])(_.closed)
        error <- F.deferred[Either[Throwable, INothing]].toResource
        closeF <- F.deferred[WSFrame.Close].toResource
        close = (closeF: DeferredSink[F, WSFrame.Close]).contramap[CloseEvent] { e =>
          WSFrame.Close(e.code, e.reason)
        }
        ws <- Resource.makeCase {
          F.async_[WebSocket] { cb =>

            if (request.method != Method.GET)
              cb(Left(new IllegalArgumentException("Must be GET Request")))
            if (!request.headers.isEmpty)
              cb(Left(new IllegalArgumentException("Custom headers are not supported")))

            val ws = new WebSocket(request.uri.renderString)
            ws.binaryType = "arraybuffer" // the default is blob

            ws.onopen = { _ =>
              ws.onerror = // replace the error handler
                e =>
                  dispatcher.unsafeRunAndForget(error.complete(Left(js.JavaScriptException(e))))
              cb(Right(ws))
            }
            
            ws.onerror = e => cb(Left(js.JavaScriptException(e)))
            ws.onmessage = e => dispatcher.unsafeRunAndForget(messages.send(e))
            ws.onclose = e => dispatcher.unsafeRunAndForget(close.complete(e) *> messages.close)
          }
        } {
          case (ws, Resource.ExitCase.Succeeded) =>
            F.delay(ws.close(1000)) // Normal Closure
          case (ws, Resource.ExitCase.Errored(ex: WSException)) =>
            F.delay(ws.close(ex.code, ex.reason))
          case (ws, Resource.ExitCase.Errored(ex)) =>
            val reason = ex.toString
            // reason must be no longer than 123 bytes of UTF-8 text
            // UTF-8 character is max 4 bytes so we can fast-path
            if (reason.length <= 30 || reason.getBytes.length <= 123)
              F.delay(ws.close(1006, reason)) // Abnormal Closure
            else
              F.delay(ws.close(1006))
          case (ws, Resource.ExitCase.Canceled) =>
            F.delay(ws.close(1006, "canceled"))
        }
      } yield new WSConnection[F] {
        def closeFrame: Deferred[F, WSFrame.Close] = closeF

        def receive: F[Option[WSDataFrame]] =
          messages
            .isClosed
            .ifM(
              none.pure,
              messages
                .stream
                .head
                .evalMap[F, WSDataFrame] { event =>
                  event.data match {
                    case s: String => WSFrame.Text(s).pure.widen
                    case b: js.typedarray.ArrayBuffer =>
                      WSFrame.Binary(ByteVector.fromJSArrayBuffer(b)).pure.widen
                    case _ =>
                      F.raiseError(
                        new WSException(1003, s"Unsupported data: ${js.typeOf(event.data)}")
                      )
                  }
                }
                .concurrently[F, WSDataFrame](
                  Stream.eval(error.get.rethrow)
                )
                .compile
                .last
            )

        def send(wsf: WSDataFrame): F[Unit] = wsf match {
          case WSFrame.Text(data, true) => F.delay(ws.send(data))
          case WSFrame.Binary(data, true) => F.delay(ws.send(data.toJSArrayBuffer))
          case _ =>
            F.raiseError(new IllegalArgumentException("DataFrames cannot be fragmented"))
        }

        def sendClose(reason: String): F[Unit] = F.delay(ws.close(reason = reason))

        def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit] =
          wsfs.foldMapM(send(_))

        def subprocotol: Option[String] = Option(ws.protocol).filter(_.nonEmpty)
      }
  }

}
