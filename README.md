# http4s-dom

Use http4s in your browser with Scala.js!

### Modules

* `core`: encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File) and other [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)s
* `fetch-client`: a `Client` implementation backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* `service-worker`: install your `HttpRoutes` as a [`FetchEvent`](https://developer.mozilla.org/en-US/docs/Web/API/FetchEvent) listener

### Usage

```sbt
libraryDependencies ++= Seq(
  "org.http4s" %%% "http4s-dom-core" % "1.0.0-M25",
  "org.http4s" %%% "http4s-dom-fetch-client" % "1.0.0-M25",
  "org.http4s" %%% "http4s-dom-service-worker" % "1.0.0-M25",
)
```
