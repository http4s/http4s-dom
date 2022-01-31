# Service Worker "Server"

The [`ServiceWorker`](https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/ServiceWorker$.html) `FetchEvent` listener integrates directly with [http4s services](https://http4s.org/v0.23/service/). You can use it to run a "proxy server" as a background process in the browser that can intercept and respond to requests made by the `FetchClient`.
