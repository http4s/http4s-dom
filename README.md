# http4s-dom

Use http4s in your browser with Scala.js!
Features:

* A [`Client` implementation](https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/FetchClientBuilder.html) backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* A [`Service Worker` integration](https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/ServiceWorker$.html) to install your `HttpRoutes` as a [`FetchEvent` handler](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/onfetch)
* Encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File), [`Blob`](https://developer.mozilla.org/en-US/docs/Web/API/Blob) and [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)

Notably, http4s-dom can also be used to create _serverless_ apps with [Cloudflare Workers](https://workers.cloudflare.com) which have adopted the same APIs used in the browser!

### Usage

[![http4s-dom Scala version support](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom/latest-by-scala-version.svg?targetType=Js)](https://index.scala-lang.org/http4s/http4s-dom/http4s-dom)

```scala
// Supports http4s 0.23.x and scala-js-dom 2.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.2.0"

// Or, for compatibility with scala-js-dom 1.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.1.0"
```
