# http4s-dom

Use http4s in your browser with Scala.js!
Features:

* A `Client` implementation backed by [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
* A [`Service Worker`](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API) integration to install your `HttpRoutes` as a [`FetchEvent` handler](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/onfetch)
* Encoders for [`File`](https://developer.mozilla.org/en-US/docs/Web/API/File), [`Blob`](https://developer.mozilla.org/en-US/docs/Web/API/Blob) and [`ReadableStream`](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)


### Usage

```sbt
// supports http4s 0.23.x and scala-js-dom 2.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.2.0"

// supports http4s 0.23.x and scala-js-dom 1.x
libraryDependencies += "org.http4s" %%% "http4s-dom" % "0.1.0"
```
