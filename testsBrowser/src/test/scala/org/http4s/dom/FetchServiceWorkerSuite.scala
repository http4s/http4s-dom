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

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import munit.CatsEffectSuite
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.client.testkit.testroutes.GetRoutes
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.http4s.syntax.all._
import org.scalajs.dom.Event
import org.scalajs.dom.ServiceWorkerRegistrationOptions
import org.scalajs.dom.window

import scala.concurrent.duration._

class FetchServiceWorkerSuite extends CatsEffectSuite {

  val client: Client[IO] = FetchClientBuilder[IO].create

  val baseUrl: Uri = uri"/"

  test("Install service worker") {
    IO.fromPromise {
      IO {
        window
          .navigator
          .serviceWorker
          .register(
            s"/${BuildInfo.workerDir}/main.js",
            new ServiceWorkerRegistrationOptions { scope = "/" }
          )
      }
    }.both(IO.async_[Unit] { cb =>
      window
        .navigator
        .serviceWorker
        .addEventListener[Event]("controllerchange", (_: Event) => cb(Right(())))
    }).timeout(1.minute)
      .attempt
      .void
  }

  test("Repeat a simple request") {
    val path = GetRoutes.SimplePath.tail

    def fetchBody = client.toKleisli(_.as[String]).local { (uri: Uri) => Request(uri = uri) }

    (0 until 10)
      .toVector
      .parTraverse(_ => fetchBody.run(baseUrl / path).map(_.length))
      .map(_.forall(_ =!= 0))
      .assert
  }

  test("POST an empty body") {
    client.expect[String](POST(baseUrl / "echo")).assertEquals("")
  }

  test("POST a normal body") {
    client
      .expect[String](POST("This is normal.", baseUrl / "echo"))
      .assertEquals("This is normal.")
  }

  test("POST a chunked body") {
    client
      .expect[String](POST(Stream("This is chunked.").covary[IO], baseUrl / "echo"))
      .assertEquals("This is chunked.")
  }

  test("POST a multipart body") {
    Multiparts.forSync[IO].flatMap { multiparts =>
      multiparts
        .multipart(Vector(Part.formData[IO]("text", "This is text.")))
        .flatMap { multipart =>
          client
            .expect[String](POST(multipart, baseUrl / "echo").withHeaders(multipart.headers))
            .map(_.contains("This is text."))
            .assert
        }
    }
  }

  GetRoutes.getPaths.toList.foreach {
    case (path, expected) =>
      test(s"Execute GET $path") {
        client
          .run(GET(baseUrl / path.tail))
          .use(resp => expected.flatMap(checkResponse(resp, _)))
          .assert
      }
  }

  private def checkResponse(rec: Response[IO], expected: Response[IO]): IO[Unit] =
    for {
      _ <- IO(rec.status).assertEquals(expected.status)
      body <- rec.body.compile.toVector
      expBody <- expected.body.compile.toVector
      _ <- IO(body == expBody).assert
      headers = rec.headers.headers
      expectedHeaders = expected.headers.headers
      _ <- IO(expectedHeaders.diff(headers)).assertEquals(Nil)
      _ <- IO(rec.httpVersion).assertEquals(expected.httpVersion)
    } yield ()

}
