# Fetch Client

The @:api(org.http4s.dom.FetchClientBuilder) creates a standard http4s @:api(org.http4s.client.Client) that is described in the [http4s documentation](https://http4s.org/v0.23/docs/client.html).

## Example

```scala mdoc:js
<div style="text-align:center">
  <h3 style="padding:10px">
    I'm bored.
  </h3>
  <button id="button">Fetch Activity</button>
  <p style="padding:10px" id="activity"></p>
</div>
---
import cats.effect._
import cats.effect.unsafe.implicits._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dom._
import org.scalajs.dom._

val client = FetchClientBuilder[IO].create

val activityElement = document.getElementById("activity")

case class Activity(activity: String)

val fetchActivity: IO[Unit] = for {
  _ <- IO(activityElement.innerHTML = "<i>fetching...</i>")
  activity <- client.expect[Activity]("https://www.boredapi.com/api/activity")
  _ <- IO(activityElement.innerHTML = activity.activity)
} yield ()

val button = document.getElementById("button").asInstanceOf[HTMLButtonElement]

button.onclick = _ => fetchActivity.unsafeRunAndForget()
```
