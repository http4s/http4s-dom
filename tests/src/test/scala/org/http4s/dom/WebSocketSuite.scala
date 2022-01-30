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
import munit.CatsEffectSuite
import org.http4s.Uri
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.http4s.dom.BuildInfo.fileServicePort
import scodec.bits.ByteVector

class WebSocketSuite extends CatsEffectSuite {

  test("send and receive frames") {
    WSClient[IO]
      .connectHighLevel(
        WSRequest(Uri.fromString(s"ws://echo.websocket.events").toOption.get))
        // WSRequest(Uri.fromString(s"ws://localhost:${fileServicePort}/ws").toOption.get))
      .use { conn =>
        for {
          _ <- conn.send(WSFrame.Binary(ByteVector(15, 2, 3)))
          _ <- conn.sendMany(List(WSFrame.Text("foo"), WSFrame.Text("bar")))
          recv <- conn.receiveStream.tail.take(3).compile.toList
          _ <- IO.println(recv)
        } yield recv
      }
      .assertEquals(
        List(
          WSFrame.Binary(ByteVector(15, 2, 3)),
          WSFrame.Text("foo"),
          WSFrame.Text("bar")
        )
      )
  }

}
