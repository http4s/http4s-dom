# Fetch Client

The @:api(org.http4s.dom.FetchClientBuilder) creates a standard http4s @:api(org.http4s.client.Client) that is described in the [http4s documentation](https://http4s.org/v0.23/docs/client.html).

## Example

```scala
libraryDependencies += "org.http4s" %%% "http4s-circe" % "@HTTP4S_VERSION@"
libraryDependencies += "io.circe" %%% "circe-generic" % "@CIRCE_VERSION@"
```

```scala mdoc:js
<div>
  <h3>How many stars?</h3>
  <input id="repo" size="36" type="text" value="http4s/http4s" placeholder="http4s/http4s">
  <button id="button">Fetch</button>
  <span id="stars" style="margin-left: 1em; color: var(--secondary-color)"><span>
</div>
---
import cats.effect._
import cats.effect.unsafe.implicits._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dom._
import org.scalajs.dom._

val client = FetchClientBuilder[IO].create

val repoName = document.getElementById("repo").asInstanceOf[HTMLInputElement]
val repoStars = document.getElementById("stars").asInstanceOf[HTMLInputElement]

case class Repo(stargazers_count: Int)

val fetchRepo: IO[Unit] = for {
  _ <- IO(repoStars.innerHTML = "<i>fetching...</i>")
  name <- IO(repoName.value)
  repo <- client.expect[Repo](s"https://api.github.com/repos/$name").attempt
  _ <- IO {
    repo match {
      case Right(Repo(stars)) => repoStars.innerHTML = s"$stars ★"
      case Left(_) => repoStars.innerHTML = s"Not found :("
    }
  }
} yield ()

val button = document.getElementById("button").asInstanceOf[HTMLButtonElement]

button.onclick = _ => fetchRepo.unsafeRunAndForget()
```
