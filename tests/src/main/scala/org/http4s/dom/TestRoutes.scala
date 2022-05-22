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

import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s.Status._
import org.http4s.client.testkit.testroutes
import scala.concurrent.duration._

object TestRoutes {
  def routes: HttpRoutes[IO] = HttpRoutes.of {
    case request =>
      val get = Some(request).filter(_.method == Method.GET).flatMap { r =>
        testroutes.GetRoutes.getPaths.get(r.uri.path.segments.last.toString)
      }

      val post =
        Some(request).filter(_.method == Method.POST).map(r => IO(Response(body = r.body)))

      get.orElse(post).getOrElse(IO(Response[IO](NotFound)))
  }
}

object GetRoutes {
  val SimplePath = "simple"
  val ChunkedPath = "chunked"
  val DelayedPath = "delayed"
  val NoContentPath = "no-content"
  val NotFoundPath = "not-found"
  val EmptyNotFoundPath = "empty-not-found"
  val InternalServerErrorPath = "internal-server-error"

  def getPaths[F[_]](implicit F: Temporal[F]): Map[String, F[Response[F]]] =
    Map(
      SimplePath -> Response[F](Ok).withEntity("simple path").pure[F],
      ChunkedPath -> Response[F](Ok)
        .withEntity(Stream.emits("chunk".toSeq.map(_.toString)).covary[F])
        .pure[F],
      DelayedPath ->
        F.sleep(1.second) *>
        Response[F](Ok).withEntity("delayed path").pure[F],
      NoContentPath -> Response[F](NoContent).pure[F],
      NotFoundPath -> Response[F](NotFound).withEntity("not found").pure[F],
      EmptyNotFoundPath -> Response[F](NotFound).pure[F],
      InternalServerErrorPath -> Response[F](InternalServerError).pure[F]
    )
}
