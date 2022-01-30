package org.http4s.dom

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.Uri
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.http4s.dom.BuildInfo.fileServicePort
import scodec.bits.ByteVector

class WebSocketSuite extends CatsEffectSuite {

  import scala.concurrent.duration._

  test("send and receive frames") {
    WSClient[IO]
      .connectHighLevel(
        WSRequest(Uri.fromString(s"ws://localhost:${fileServicePort}/ws").toOption.get))
      .use { conn =>
        for {
          _ <- conn.send(WSFrame.Binary(ByteVector(15, 2, 3)))
          _ <- conn.sendMany(List(WSFrame.Text("foo"), WSFrame.Text("bar")))
          _ <- IO.sleep(1.second)
          _ <- conn.sendClose()
          recv <- conn.receiveStream.compile.toList
          _ <- IO.println(recv)
          _ <- IO.sleep(1.second)
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
