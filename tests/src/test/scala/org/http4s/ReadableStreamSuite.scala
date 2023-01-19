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

import cats.effect.IO
import fs2.Chunk
import fs2.Stream
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.effect.PropF.forAllF

import scala.concurrent.duration._

class ReadableStreamSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMaxSize(20)

  test("to/read ReadableStream") {
    forAllF {
      (chunks: Vector[Vector[Byte]], offerSleeps: Vector[Int], takeSleeps: Vector[Int]) =>
        def snooze(sleeps: Vector[Int]): Stream[IO, Unit] =
          Stream
            .emits(sleeps)
            .ifEmpty(Stream.emit(0))
            .repeat
            .evalMap(d => IO.sleep((d & 3).millis))

        Stream
          .emits(chunks)
          .map(Chunk.seq(_))
          .zipLeft(snooze(offerSleeps))
          .unchunks
          .through(in => Stream.resource(toReadableStream[IO](in)))
          .flatMap(readable => readReadableStream(IO(readable)))
          .zipLeft(snooze(takeSleeps))
          .compile
          .toVector
          .assertEquals(chunks.flatten)
    }
  }

}
