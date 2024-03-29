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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Supervisor
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import org.scalajs.dom.AbortController
import org.scalajs.dom.Fetch
import org.scalajs.dom.FetchEvent
import org.scalajs.dom.RequestInit
import org.scalajs.dom.ResponseInit
import org.scalajs.dom.ServiceWorkerGlobalScope
import org.scalajs.dom.{Response => DomResponse}
import org.typelevel.vault.Key

import scala.scalajs.js

object ServiceWorker {

  /**
   * Adds a listener for `FetchEvent`. If the event is not intercepted by `routes` then it is
   * treated as an ordinary request. Additional context can be retrieved via
   * [[FetchEventContext]] including a [[cats.effect.std.Supervisor]] for running background
   * tasks after returning the response.
   */
  def addFetchEventListener[F[_]](routes: HttpRoutes[F], contextKey: Key[FetchEventContext[F]])(
      implicit F: Async[F]
  ): Resource[F, Unit] = Dispatcher.parallel.flatMap { dispatcher =>
    val jsHandler: js.Function1[FetchEvent, Unit] = { event =>
      event.respondWith {
        dispatcher.unsafeToPromise {
          Supervisor[F](await = true).allocated.flatMap {
            case (supervisor, await) =>
              val response = routesToListener(routes, supervisor, contextKey).run(event)
              def fallback = F.fromPromiseCancelable {
                F.delay {
                  val ctrl = new AbortController
                  val response =
                    Fetch.fetch(event.request, new RequestInit { signal = ctrl.signal })
                  val cancel = F.delay(ctrl.abort())
                  (response, cancel)
                }
              }
              response.getOrElseF(fallback) <*
                F.delay(event.waitUntil(dispatcher.unsafeToPromise(await)))
          }
        }
      }
    }

    Resource.make {
      F.delay(ServiceWorkerGlobalScope.self.addEventListener("fetch", jsHandler))
    }(_ => F.delay(ServiceWorkerGlobalScope.self.removeEventListener("fetch", jsHandler)))
  }

  /**
   * Adds a listener for `FetchEvent`. If the event is not intercepted by `routes` then it is
   * treated as an ordinary request. Additional context can be retrieved via
   * [[FetchEventContext]] including a [[cats.effect.std.Supervisor]] for running background
   * tasks after returning the response.
   */
  def addFetchEventListener(routes: HttpRoutes[IO]): Resource[IO, Unit] =
    addFetchEventListener(routes, FetchEventContext.IOKey)

  @deprecated("Use overload that directly takes routes and returns Resource", "0.2.8")
  def addFetchEventListener(routes: IO[HttpRoutes[IO]])(
      implicit runtime: IORuntime): SyncIO[SyncIO[Unit]] = for {
    handler <- Deferred.in[SyncIO, IO, Either[Throwable, HttpRoutes[IO]]]
    _ <- SyncIO(routes.attempt.flatMap(handler.complete).unsafeRunAndForget())
    jsHandler = { event =>
      event.respondWith(
        (for {
          supervisorResource <- Supervisor[IO].allocated
          (supervisor, closeSupervisor) = supervisorResource
          res <- OptionT
            .liftF(handler.get.rethrow)
            .flatMap(routesToListener(_, supervisor, FetchEventContext.IOKey).apply(event))
            .getOrElseF(IO.fromPromise(IO(Fetch.fetch(event.request))))
          _ <- IO(event.waitUntil(closeSupervisor.unsafeToPromise()))
        } yield res).unsafeToPromise()
      )
    }: scalajs.js.Function1[FetchEvent, Unit]
    _ <- SyncIO(ServiceWorkerGlobalScope.self.addEventListener("fetch", jsHandler))
  } yield SyncIO(ServiceWorkerGlobalScope.self.removeEventListener("fetch", jsHandler))

  private type FetchEventListener[F[_]] = Kleisli[OptionT[F, *], FetchEvent, DomResponse]

  private def routesToListener[F[_]](
      routes: HttpRoutes[F],
      supervisor: Supervisor[F],
      key: Key[FetchEventContext[F]])(implicit F: Async[F]): FetchEventListener[F] =
    Kleisli { event =>
      val OptionF = Async[OptionT[F, *]]
      val req = event.request
      for {
        method <- OptionF.fromEither(Method.fromString(req.method.toString))
        uri <- OptionF.fromEither(Uri.fromString(req.url))
        headers = fromDomHeaders(req.headers)
        body = Stream
          .evalUnChunk(F.fromPromise(F.delay(req.arrayBuffer())).map(Chunk.jsArrayBuffer))
          .covary[F]
        request = Request(method, uri, headers = headers, body = body)
          .withAttribute(key, FetchEventContext(event, supervisor))
        response <- routes(request)
        body <- OptionT.liftF(
          response.body.chunkAll.filter(_.nonEmpty).map(_.toJSArrayBuffer).compile.last
        )
      } yield new DomResponse(
        body.getOrElse(null),
        new ResponseInit {
          this.status = response.status.code
          this.statusText = response.status.reason
          this.headers = toDomHeaders(response.headers, request = false)
        }
      )
    }

}
