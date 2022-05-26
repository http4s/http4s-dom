# WebSocket Client

The @:api(org.http4s.dom.WebSocketClient$) implements the http4s @:api(org.http4s.client.websocket.WSClientHighLevel) interface.

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

val message = document.getElementById("message").asInstanceOf[HTMLInputElement]
val button = document.getElementById("button").asInstanceOf[HTMLButtonElement]
val sent = document.getElementById("sent").asInstanceOf[HTMLElement]
val received = document.getElementById("received").asInstanceOf[HTMLElement]

val request = WSRequest(uri"wss://ws.postman-echo.com/raw")
val app = WebSocketClient[IO].connectHighLevel(request).use { conn =>

  def log(e: HTMLElement, text: String): IO[Unit] =
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
