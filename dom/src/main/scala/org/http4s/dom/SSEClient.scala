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

package org.http4s
package dom

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import fs2.Stream
import fs2.concurrent.Channel
import org.scalajs.dom.AbortController
import org.scalajs.dom.EventListenerOptions
import org.scalajs.dom.EventSource
import org.scalajs.dom.MessageEvent

import scala.scalajs.js

trait SSEClient[F[_]] {
  def connect(url: Uri): Resource[F, SSEConnection[F]] = connect(url, false)
  def connect(url: Uri, withCredentials: Boolean): Resource[F, SSEConnection[F]]
}

trait SSEConnection[F[_]] {
  def subscribe: Stream[F, String] = subscribe("message")
  def subscribe(eventName: String): Stream[F, String]
}

object SSEClient {

  def apply[F[_]](implicit F: Async[F]): SSEClient[F] = new SSEClient[F] {

    def connect(url: Uri, withCredentials: Boolean): Resource[F, SSEConnection[F]] =
      Dispatcher[F].flatMap { dispatcher =>
        Resource
          .make(F.async_[EventSource] { cb =>
            val eventSource = new EventSource(url.renderString)
            eventSource.onopen = _ => cb(Right(eventSource))
            eventSource.onerror = e => cb(Left(js.JavaScriptException(e)))
          })(es => F.delay(es.close()))
          .map { eventSource => eventName =>
            for {
              ch <- Stream.eval(Channel.unbounded[F, MessageEvent])
              ac <- Stream.bracket(F.delay(new AbortController))(c => F.delay(c.abort()))
              _ <- Stream.eval(
                F.delay(
                  eventSource.addEventListener[MessageEvent](
                    eventName,
                    e => dispatcher.unsafeRunAndForget(ch.send(e)),
                    new EventListenerOptions { signal = ac.signal }
                  )
                )
              )
              event <- ch.stream
            } yield event.data.asInstanceOf[String] // darn these JS APIs
          }
      }
  }
}
