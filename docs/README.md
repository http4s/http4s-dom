# http4s-dom

## Installation

```scala
// Supports http4s 0.23.x and scala-js-dom 2.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.2.0"

// Or, for compatibility with scala-js-dom 1.x
// libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.1.0"

// recommended, brings in the latest client module
libraryDependencies += "org.http4s" %%% "http4s-client" % "0.23.6"

// optional, for JSON support
libraryDependencies += "org.http4s" %%% "http4s-circe" % "0.23.6"
libraryDependencies += "io.circe" %%% "circe-generic" % "0.15.0-M1"
```

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

final case class Activity(activity: String)

val fetchActivity: IO[Unit] = for {
  _ <- IO(activityElement.innerHTML = "<i>fetching...</i>")
  activity <- client.expect[Activity]("https://www.boredapi.com/api/activity")
  _ <- IO(activityElement.innerHTML = activity.activity)
} yield ()

val button =
  document.getElementById("button").asInstanceOf[html.Button]

button.onclick = _ => fetchActivity.unsafeRunAndForget()
```

## Learn more

The [`FetchClientBuilder`](https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/FetchClientBuilder.html) creates a standard http4s [`Client`](https://http4s.org/v0.23/api/org/http4s/client/client) that is described in the [http4s documentation](https://http4s.org/v0.23/client/).