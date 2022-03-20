# http4s-dom

Use http4s in your browser with Scala.js!
Features:

* A [`Client` implementation](fetch.md) backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* A [`WSClient` implementation](websocket.md) backed by [`WebSocket`](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket$.html)
* A [`Service Worker` integration](serviceworker.md) to install your `HttpRoutes` as a [`FetchEvent` handler](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/onfetch)
* Encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File), [`Blob`](https://developer.mozilla.org/en-US/docs/Web/API/Blob) and [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)

Notably, http4s-dom can also be used to create _serverless_ apps with [Cloudflare Workers](https://workers.cloudflare.com) which have adopted the same APIs used in the browser!

## Installation

[![http4s-dom Scala version support](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom/latest.svg)](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom)

```scala
// Supports http4s 0.23.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "@VERSION@"

// recommended, brings in the latest client module
libraryDependencies += "org.http4s" %%% "http4s-client" % "@HTTP4S_VERSION@"

// optional, for JSON support
libraryDependencies += "org.http4s" %%% "http4s-circe" % "@HTTP4S_VERSION@"
libraryDependencies += "io.circe" %%% "circe-generic" % "@CIRCE_VERSION@"
```
