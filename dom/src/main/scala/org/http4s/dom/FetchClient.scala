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

import cats.effect.Async
import cats.effect.Poll
import cats.effect.Resource
import cats.effect.syntax.all._
import cats.syntax.all._
import org.http4s.client.Client
import org.http4s.headers.Referer
import org.scalajs.dom.AbortController
import org.scalajs.dom.Fetch
import org.scalajs.dom.Headers
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.dom.{Response => FetchResponse}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

private[dom] object FetchClient {

  private[dom] def makeClient[F[_]](
      requestTimeout: Duration,
      options: FetchOptions
  )(implicit F: Async[F]): Client[F] = Client[F] { (req: Request[F]) =>
    Resource.eval(req.body.chunkAll.filter(_.nonEmpty).compile.last).flatMap { body =>
      Resource
        .makeCaseFull { (poll: Poll[F]) =>
          F.delay(new AbortController).flatMap { abortController =>
            val requestOptions = req.attributes.lookup(FetchOptions.Key)
            val mergedOptions = requestOptions.fold(options)(options.merge)

            val init = new RequestInit {}

            init.method = req.method.name.asInstanceOf[HttpMethod]
            init.headers = new Headers(toDomHeaders(req.headers))
            body.foreach { body => init.body = body.toJSArrayBuffer }
            init.signal = abortController.signal
            mergedOptions.cache.foreach(init.cache = _)
            mergedOptions.credentials.foreach(init.credentials = _)
            mergedOptions.integrity.foreach(init.integrity = _)
            mergedOptions.keepAlive.foreach(init.keepalive = _)
            mergedOptions.mode.foreach(init.mode = _)
            mergedOptions.redirect.foreach(init.redirect = _)
            // Referer headers are forbidden in Fetch, but we make a best effort to preserve behavior across clients.
            // See https://developer.mozilla.org/en-US/docs/Glossary/Forbidden_header_name
            // If there's a Referer header, it will have more priority than the client's `referrer` (if present)
            // but less priority than the request's `referrer` (if present).
            requestOptions
              .flatMap(_.referrer)
              .orElse(req.headers.get[Referer].map(_.uri))
              .orElse(options.referrer)
              .foreach(referrer => init.referrer = referrer.renderString)
            mergedOptions.referrerPolicy.foreach(init.referrerPolicy = _)

            val fetch = poll(F.fromPromise(F.delay(Fetch.fetch(req.uri.renderString, init))))
              .onCancel(F.delay(abortController.abort()))

            requestTimeout match {
              case d: FiniteDuration =>
                fetch.timeoutTo(
                  d,
                  F.raiseError[FetchResponse](new TimeoutException(
                    s"Request to ${req.uri.renderString} timed out after ${d.toMillis} ms"))
                )
              case _ =>
                fetch
            }
          }
        } {
          case (r, exitCase) =>
            closeReadableStream(r.body, exitCase)
        }
        .evalMap(fromDomResponse[F])

    }
  }

}
