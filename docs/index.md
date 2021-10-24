# http4s-dom

### Installation

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

### Example

```scala mdoc:js
<div style="text-align:center">
  <p style="padding:10px">
    Can I haz dad joke?
  </p>
  <button id="button">Fetch</button>
  <p style="padding:10px" id="joke"></p>
</div>
---
import cats.effect._
import cats.effect.unsafe.implicits._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dom._
import org.scalajs.dom._

val client = FetchClientBuilder[IO].create

val jokeElement = document.getElementById("joke")

final case class Joke(joke: String)

val fetchJoke: IO[Unit] = for {
  joke <- client.expect[Joke]("https://icanhazdadjoke.com/")
  _ <- IO(jokeElement.innerHTML = joke.joke)
} yield ()

val button =
  document.getElementById("button").asInstanceOf[html.Button]

button.onclick = _ => fetchJoke.unsafeRunAndForget()
```

### Learn more

Check out the http4s [client documentation](https://http4s.org/v0.23/client/).
