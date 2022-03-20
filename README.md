# http4s-dom

Use http4s in your browser with Scala.js! Check out the [interactive examples](https://http4s.github.io/http4s-dom/) in the docs.

Features:

* A [`Client` implementation](fetch.md) backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* A [`WSClient` implementation](websocket.md) backed by [`WebSocket`](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket$.html)
* A [`Service Worker` integration](serviceworker.md) to install your `HttpRoutes` as a [`FetchEvent` handler](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/onfetch)
* Encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File), [`Blob`](https://developer.mozilla.org/en-US/docs/Web/API/Blob) and [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)

Notably, http4s-dom can also be used to create _serverless_ apps with [Cloudflare Workers](https://workers.cloudflare.com) which have adopted the same APIs used in the browser!

### Usage

[![http4s-dom Scala version support](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom/latest.svg)](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom)

```scala
// Supports http4s 0.23.x and scala-js-dom 2.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.2.1"
```
