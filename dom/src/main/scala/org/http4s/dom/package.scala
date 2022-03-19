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

package org.http4s

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all._
import fs2.Stream
import org.http4s.client.websocket
import org.scalajs.dom.Blob
import org.scalajs.dom.File
import org.scalajs.dom.ReadableStream
import org.scalajs.dom.{Headers => DomHeaders}
import org.scalajs.dom.{Response => DomResponse}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array

package object dom {

  type WSClient[F[_]] = websocket.WSClientHighLevel[F]
  type WSConnection[F[_]] = websocket.WSConnectionHighLevel[F]
  type WSRequest = websocket.WSRequest
  val WSRequest = websocket.WSRequest
  type WSFrame = websocket.WSFrame
  val WSFrame = websocket.WSFrame
  type WSDataFrame = websocket.WSDataFrame

  implicit def fileEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, File] =
    blobEncoder.narrow

  implicit def blobEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, Blob] =
    EntityEncoder.entityBodyEncoder.contramap { blob =>
      Stream
        .bracketCase {
          F.delay(blob.stream())
        } { case (rs, exitCase) => closeReadableStream(rs, exitCase) }
        .flatMap(fromReadableStream[F])
    }

  implicit def readableStreamEncoder[F[_]: Async]
      : EntityEncoder[F, ReadableStream[Uint8Array]] =
    EntityEncoder.entityBodyEncoder.contramap { rs => fromReadableStream(rs) }

  private[dom] def fromDomResponse[F[_]](response: DomResponse)(
      implicit F: Async[F]): F[Response[F]] =
    F.fromEither(Status.fromInt(response.status)).map { status =>
      Response[F](
        status = status,
        headers = fromDomHeaders(response.headers),
        body = fromReadableStream(response.body)
      )
    }

  private[dom] def toDomHeaders(headers: Headers): DomHeaders =
    new DomHeaders(
      headers
        .headers
        .view
        .map {
          case Header.Raw(name, value) =>
            name.toString -> value
        }
        .toMap
        .toJSDictionary)

  private[dom] def fromDomHeaders(headers: DomHeaders): Headers =
    Headers(
      headers.map { header => header(0) -> header(1) }.toList
    )

  private[dom] def fromReadableStream[F[_]](rs: ReadableStream[Uint8Array])(
      implicit F: Async[F]): Stream[F, Byte] =
    Stream.bracket(F.delay(rs.getReader()))(r => F.delay(r.releaseLock())).flatMap { reader =>
      Stream.unfoldChunkEval(reader) { reader =>
        F.fromPromise(F.delay(reader.read())).map { chunk =>
          if (chunk.done)
            None
          else
            Some((fs2.Chunk.uint8Array(chunk.value), reader))
        }
      }
    }

  private[dom] def closeReadableStream[F[_], A](
      rs: ReadableStream[A],
      exitCase: Resource.ExitCase)(implicit F: Async[F]): F[Unit] = F.fromPromise {
    F.delay {
      // Best guess: Firefox internally locks a ReadableStream after it is "drained"
      // This checks if the stream is locked before canceling it to avoid an error
      if (!rs.locked) exitCase match {
        case Resource.ExitCase.Succeeded =>
          rs.cancel(js.undefined)
        case Resource.ExitCase.Errored(ex) =>
          rs.cancel(ex.getLocalizedMessage())
        case Resource.ExitCase.Canceled =>
          rs.cancel(js.undefined)
      }
      else js.Promise.resolve[Unit](())
    }
  }.void

}
