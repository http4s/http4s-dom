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
import cats.effect.syntax.all._
import cats.syntax.all._
import org.http4s.client.websocket.WSClientHighLevel
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import scodec.bits.ByteVector
import scalajs.js.|

import scala.scalajs.js
import cats.effect.kernel.Sync
import org.scalajs.dom.ReadableStream

// https://developer.mozilla.org/en-US/docs/Web/API/WebSocketStream
object WebSocketStreamClient {
  def apply[F[_]: Async]: WSClientHighLevel[F] = new WSClientHighLevel[F] {
    def connectHighLevel(
        request: WSRequest
    ): Resource[F, WSConnectionHighLevel[F]] =
      for {
        stream <- Resource.make {
          Sync[F].delay(new facades.WebSocketStream(request.uri.renderString))
        } { stream => Sync[F].delay(stream.close()) }

        opened <- Async[F].fromPromise(Sync[F].delay(stream.opened)).toResource
        reader <- Sync[F].delay(opened.readable.getReader()).toResource
      } yield {

        new WSConnectionHighLevel[F] {
          // TODO
          def closeFrame: DeferredSource[F, WSFrame.Close] = ???

          // TODO
          def send(wsf: WSDataFrame): F[Unit] = ???

          def sendMany[G[_]: Foldable, A <: WSDataFrame](
              wsfs: G[A]
          ): F[Unit] = ???

          def receive: F[Option[WSDataFrame]] = Async[F]
            .fromPromise {
              Sync[F].delay {
                reader.read()
              }
            }
            .map { chunk =>
              (chunk.value: Any) match {
                case _ if chunk.done => None

                // todo: do we ever have a "last"?
                case text: String =>
                  Some(WSFrame.Text(text, last = false))

                case bytes: js.typedarray.Uint8Array =>
                  Some(
                    WSFrame.Binary(ByteVector.fromUint8Array(bytes), last = false)
                  )

                // shouldn't happen
                case _ => throw new AssertionError

              }

            }

          def subprotocol: Option[String] = ???
        }
      }
  }

  // https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Using_WebSocketStream
  // https://github.com/whatwg/websockets/pull/48
  private object facades {

    @js.native
    @js.annotation.JSGlobal("WebSocketStream")
    class WebSocketStream(var url: String) extends js.Any {
      def opened: js.Promise[WebSocketStreamOpened] = js.native
      def close(): Unit = js.native
    }

    @js.native
    trait WebSocketStreamOpened extends js.Object {
      val readable: ReadableStream[String | js.typedarray.Uint8Array]
      // val writable: WriteableStream[???]
    }

  }
}
