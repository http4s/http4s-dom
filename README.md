# http4s-dom

Use http4s in your browser with Scala.js! Check out the [interactive examples](https://http4s.github.io/http4s-dom/) in the docs.

Features:

* A [`Client` implementation](https://http4s.github.io/http4s-dom/fetch.html) backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* A [`WSClient` implementation](https://http4s.github.io/http4s-dom/websocket.html) backed by [`WebSocket`](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
* A [`Service Worker` integration](https://http4s.github.io/http4s-dom/serviceworker.html) to install your `HttpRoutes` as a [`FetchEvent` handler](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/fetch_event)
* Encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File), [`Blob`](https://developer.mozilla.org/en-US/docs/Web/API/Blob) and [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)

Notably, http4s-dom can also be used to create _serverless_ apps with [Cloudflare Workers](https://workers.cloudflare.com) which have adopted the same APIs used in the browser!

It is also possible to use the `FetchClient` in Node.js v18+, which added [experimental support](https://nodejs.org/en/blog/announcements/v18-release-announce/#fetch-experimental) for `fetch`, although some browser-specific features may not be available.

### Usage

[![http4s-dom Scala version support](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom/latest.svg)](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom)

```scala
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.2.3"
```
