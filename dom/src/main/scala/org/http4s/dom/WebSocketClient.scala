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
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.INothing
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
        drained <- F.deferred[Unit].toResource
        error <- F.deferred[Either[Throwable, INothing]].toResource
        close <- F.deferred[CloseEvent].toResource
        ws <- Resource.makeCase {
          F.async_[WebSocket] { cb =>
            if (request.method != Method.GET)
              cb(Left(new IllegalArgumentException("Must be GET Request")))

            val protocols = request
              .headers
              .get(ci"Sec-WebSocket-Protocol")
              .fold(List.empty[String])(_.toList.map(_.value))

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
                if (ws.readyState == 3) // already closed
                  ws.onerror = e => cb(Left(js.JavaScriptException(e)))
                ws.onclose = e => cb(Right(e))
                reason match { // 1000 "normal closure" is only code supported in browser
                  case Some(reason) => ws.close(1000, reason)
                  case None => ws.close(1000)
                }
              }
              .flatMap(close.complete(_)) *> messages.offer(None)

            close.tryGet.map(_.isEmpty).ifM(shutdown, F.unit)
        }
      } yield new WSConnectionHighLevel[F] {

        def closeFrame: DeferredSource[F, WSFrame.Close] =
          (close: DeferredSource[F, CloseEvent]).map(e => WSFrame.Close(e.code, e.reason))

        def receive: F[Option[WSDataFrame]] =
          drained
            .get
            .race(messages.take.flatMap[Option[WSDataFrame]] {
              case None => drained.complete(()).as(none)
              case Some(e) =>
                e.data match {
                  case s: String => WSFrame.Text(s).some.pure.widen
                  case b: js.typedarray.ArrayBuffer =>
                    WSFrame.Binary(ByteVector.fromJSArrayBuffer(b)).some.pure.widen
                  case _ =>
                    F.raiseError(
                      new WebSocketException(s"Unsupported data: ${js.typeOf(e.data)}")
                    )
                }
            })
            .map(_.toOption.flatten)
            .race(error.get.rethrow)
            .map(_.merge)

        def send(wsf: WSDataFrame): F[Unit] = errorOr {
          wsf match {
            case WSFrame.Text(data, true) => F.delay(ws.send(data))
            case WSFrame.Binary(data, true) => F.delay(ws.send(data.toJSArrayBuffer))
            case _ =>
              F.raiseError(new IllegalArgumentException("DataFrames cannot be fragmented"))
          }
        }

        private def errorOr(fu: F[Unit]): F[Unit] = error.tryGet.flatMap {
          case Some(error) => F.rethrow[Unit, Throwable](error.pure.widen)
          case None => fu
        }

        def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit] =
          wsfs.foldMapM(send(_))

        def subprotocol: Option[String] = Option(ws.protocol).filter(_.nonEmpty)
      }
  }

}
