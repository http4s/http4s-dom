/*
 * Copyright 2021 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.dom

import cats.effect.Async
import cats.effect.Resource
import org.http4s.client.Client
import org.http4s.client.defaults
import org.http4s.internal.BackendBuilder
import org.scalajs.dom.ReferrerPolicy
import org.scalajs.dom.RequestCache
import org.scalajs.dom.RequestCredentials
import org.scalajs.dom.RequestMode
import org.scalajs.dom.RequestRedirect

import scala.concurrent.duration._

/**
 * Configure and obtain a FetchClient
 *
 * @param requestTimeout
 *   maximum duration from the submission of a request through reading the body before a
 *   timeout.
 * @param cache
 *   how the request will interact with the browser’s HTTP cache
 * @param credentials
 *   what browsers do with credentials (cookies, HTTP authentication entries, and TLS client
 *   certificates)
 * @param mode
 *   mode you want to use for the request, e.g., cors, no-cors, or same-origin
 * @param redirect
 *   how to handle a redirect response
 * @param referrer
 *   referrer of the request
 * @param referrerPolicy
 *   referrer policy to use for the request
 */
sealed abstract class FetchClientBuilder[F[_]] private (
    val requestTimeout: Duration,
    val cache: Option[RequestCache],
    val credentials: Option[RequestCredentials],
    val mode: Option[RequestMode],
    val redirect: Option[RequestRedirect],
    val referrer: Option[FetchReferrer],
    val referrerPolicy: Option[ReferrerPolicy],
    val streamingRequests: Boolean
)(override implicit protected val F: Async[F])
    extends BackendBuilder[F, Client[F]] {

  private def copy(
      requestTimeout: Duration = requestTimeout,
      cache: Option[RequestCache] = cache,
      credentials: Option[RequestCredentials] = credentials,
      mode: Option[RequestMode] = mode,
      redirect: Option[RequestRedirect] = redirect,
      referrer: Option[FetchReferrer] = referrer,
      referrerPolicy: Option[ReferrerPolicy] = referrerPolicy,
      streamingRequests: Boolean = streamingRequests
  ): FetchClientBuilder[F] =
    new FetchClientBuilder[F](
      requestTimeout,
      cache,
      credentials,
      mode,
      redirect,
      referrer,
      referrerPolicy,
      streamingRequests
    ) {}

  def withRequestTimeout(requestTimeout: Duration): FetchClientBuilder[F] =
    copy(requestTimeout = requestTimeout)

  def withCacheOption(cache: Option[RequestCache]): FetchClientBuilder[F] =
    copy(cache = cache)
  def withCache(cache: RequestCache): FetchClientBuilder[F] =
    withCacheOption(Some(cache))
  def withDefaultCache: FetchClientBuilder[F] =
    withCacheOption(None)

  def withCredentialsOption(credentials: Option[RequestCredentials]): FetchClientBuilder[F] =
    copy(credentials = credentials)
  def withCredentials(credentials: RequestCredentials): FetchClientBuilder[F] =
    withCredentialsOption(Some(credentials))
  def withDefaultCredentials: FetchClientBuilder[F] =
    withCredentialsOption(None)

  def withModeOption(mode: Option[RequestMode]): FetchClientBuilder[F] =
    copy(mode = mode)
  def withMode(mode: RequestMode): FetchClientBuilder[F] =
    withModeOption(Some(mode))
  def withDefaultMode: FetchClientBuilder[F] =
    withModeOption(None)

  def withRedirectOption(redirect: Option[RequestRedirect]): FetchClientBuilder[F] =
    copy(redirect = redirect)
  def withRedirect(redirect: RequestRedirect): FetchClientBuilder[F] =
    withRedirectOption(Some(redirect))
  def withDefaultRedirect: FetchClientBuilder[F] =
    withRedirectOption(None)

  def withReferrerOption(referrer: Option[FetchReferrer]): FetchClientBuilder[F] =
    copy(referrer = referrer)
  def withReferrer(referrer: FetchReferrer): FetchClientBuilder[F] =
    withReferrerOption(Some(referrer))
  def withDefaultReferrer: FetchClientBuilder[F] =
    withReferrerOption(None)

  def withReferrerPolicyOption(referrerPolicy: Option[ReferrerPolicy]): FetchClientBuilder[F] =
    copy(referrerPolicy = referrerPolicy)
  def withReferrerPolicy(referrerPolicy: ReferrerPolicy): FetchClientBuilder[F] =
    withReferrerPolicyOption(Some(referrerPolicy))
  def withDefaultReferrerPolicy: FetchClientBuilder[F] =
    withReferrerPolicyOption(None)

  def withStreamingRequests: FetchClientBuilder[F] =
    copy(streamingRequests = true)
  def withoutStreamingRequests: FetchClientBuilder[F] =
    copy(streamingRequests = false)

  /**
   * Creates a `Client`.
   */
  def create: Client[F] = FetchClient.makeClient(
    requestTimeout,
    FetchOptions(
      cache = cache,
      credentials = credentials,
      integrity = None,
      keepAlive = None,
      mode = mode,
      redirect = redirect,
      referrer = referrer,
      referrerPolicy = referrerPolicy,
      streamingRequests = streamingRequests
    )
  )

  override def resource: Resource[F, Client[F]] =
    Resource.pure(create)
}

object FetchClientBuilder {

  /**
   * Creates a FetchClientBuilder
   */
  def apply[F[_]: Async]: FetchClientBuilder[F] =
    new FetchClientBuilder[F](
      requestTimeout = defaults.RequestTimeout,
      cache = None,
      credentials = None,
      mode = None,
      redirect = None,
      referrer = None,
      referrerPolicy = None,
      streamingRequests = false
    ) {}
}
