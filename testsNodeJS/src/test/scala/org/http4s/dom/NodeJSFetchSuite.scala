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
import cats.effect.Resource
import fs2.Stream
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.client.testkit.ClientRouteTestBattery

import scala.concurrent.duration._

class NodeJSFetchSuite extends ClientRouteTestBattery("FetchClient") {
  def clientResource: Resource[IO, Client[IO]] = FetchClientBuilder[IO].resource

  test("POST a chunked body with streaming requests") {
    val address = server().addresses.head
    val baseUrl = Uri.fromString(s"http://$address/").toOption.get
    FetchClientBuilder[IO]
      .withStreamingRequests
      .create
      .expect[String](POST(Stream("This is chunked.").covary[IO], baseUrl / "echo"))
      .assertEquals("This is chunked.")
  }

  test("Cancel an in-flight request") {
    val address = server().addresses.head
    client()
      .expect[String](s"http://$address/delayed")
      .void
      .timeoutTo(100.millis, IO.unit)
      .timed
      .flatMap {
        case (duration, _) =>
          IO(assert(clue(duration) < 500.millis))
      }
  }
}
