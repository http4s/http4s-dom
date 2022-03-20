# WebSocket Client

The [`WebSocketClient`](https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/WSClient$.html) creates a standard http4s [`WSClientHighLevel`](https://http4s.org/v0.23/api/org/http4s/client/client).

## Example

```scala mdoc:js
<div>
  <h3>Send Text Frame</h3>
  <input id="message" size="64" type="text">
  <button id="button">Send</button>
</div>
<div style="display: flex">
  <div style="flex: 50%">
    <h3>Sent</h3>
    <div id="sent" style="width: 100%"></div>
  </div>
  <div style="flex: 50%">
    <h3>Received</h3>
    <div id="received" style="width: 100%"></div>
  </div>
</div>
---
import cats.effect._
import cats.effect.unsafe.implicits._
import org.http4s.client.websocket._
import org.http4s.dom._
import org.http4s.syntax.all._
import org.scalajs.dom._

val message = document.getElementById("message").asInstanceOf[html.Input]
val button = document.getElementById("button").asInstanceOf[html.Button]
val sent = document.getElementById("sent").asInstanceOf[html.Element]
val received = document.getElementById("received").asInstanceOf[html.Element]

val request = WSRequest(uri"wss://ws.postman-echo.com/raw")
val app = WebSocketClient[IO].connectHighLevel(request).use { conn =>

  def log(e: html.Element, text: String): IO[Unit] =
    IO {
      val p = document.createElement("p")
      p.innerHTML = text
      e.appendChild(p)
      ()
    }

  val sendMessage: IO[Unit] = for {
    text <- IO(message.value)
    frame = WSFrame.Text(text)
    _ <- conn.send(frame)
    _ <- log(sent, frame.toString)
  } yield ()

  val receiveMessages: IO[Unit] =
    conn.receiveStream
      .evalTap(frame => log(received, frame.toString))
      .compile
      .drain

  val logCloseFrame: IO[Unit] =
    conn.closeFrame.get.flatMap(frame => log(received, frame.toString))

  val registerOnClick = IO(button.onclick = _ => sendMessage.unsafeRunAndForget())
  val deregisterOnClick = IO(button.onclick = null)

  registerOnClick *> receiveMessages *> logCloseFrame *> deregisterOnClick
}

app.unsafeRunAndForget()
```
