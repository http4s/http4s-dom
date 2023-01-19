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
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import org.http4s.headers.`Transfer-Encoding`
import org.scalajs.dom.Blob
import org.scalajs.dom.Fetch
import org.scalajs.dom.File
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.ReadableStream
import org.scalajs.dom.ReadableStreamType
import org.scalajs.dom.ReadableStreamUnderlyingSource
import org.scalajs.dom.RequestDuplex
import org.scalajs.dom.RequestInit
import org.scalajs.dom.{Headers => DomHeaders}
import org.scalajs.dom.{Request => DomRequest}
import org.scalajs.dom.{Response => DomResponse}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

package object dom {

  implicit def fileEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, File] =
    blobEncoder.narrow

  implicit def blobEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, Blob] =
    EntityEncoder.entityBodyEncoder.contramap { blob =>
      readReadableStream[F](F.delay(blob.stream()))
    }

  implicit def readableStreamEncoder[F[_]: Async]
      : EntityEncoder[F, ReadableStream[Uint8Array]] =
    EntityEncoder.entityBodyEncoder.contramap { rs => readReadableStream(rs.pure) }

  private[dom] def fromDomResponse[F[_]](response: DomResponse)(
      implicit F: Async[F]): F[Response[F]] =
    F.fromEither(Status.fromInt(response.status)).map { status =>
      Response[F](
        status = status,
        headers = fromDomHeaders(response.headers),
        body = Stream.fromOption(Option(response.body)).flatMap { rs =>
          readReadableStream[F](rs.pure)
        }
      )
    }

  private[dom] def toDomHeaders(headers: Headers, request: Boolean): DomHeaders = {
    val domHeaders = new DomHeaders()
    headers.foreach {
      case Header.Raw(name, value) =>
        val skip = request && name == `Transfer-Encoding`.name
        if (!skip) domHeaders.append(name.toString, value)
    }
    domHeaders
  }

  private[dom] def fromDomHeaders(headers: DomHeaders): Headers =
    Headers(
      headers.map { header => header(0) -> header(1) }.toList
    )

  private[dom] def readReadableStream[F[_]](
      readableStream: F[ReadableStream[Uint8Array]]
  )(implicit F: Async[F]): Stream[F, Byte] = {
    def read(readableStream: ReadableStream[Uint8Array]) =
      Stream
        .bracket(F.delay(readableStream.getReader()))(r => F.delay(r.releaseLock()))
        .flatMap { reader =>
          Stream.unfoldChunkEval(reader) { reader =>
            F.fromPromise(F.delay(reader.read())).map { chunk =>
              if (chunk.done)
                None
              else
                Some((fs2.Chunk.uint8Array(chunk.value), reader))
            }
          }
        }

    Stream.bracketCase(readableStream)(cancelReadableStream(_, _)).flatMap(read(_))
  }

  private[dom] def cancelReadableStream[F[_], A](
      rs: ReadableStream[A],
      exitCase: Resource.ExitCase
  )(implicit F: Async[F]): F[Unit] = F.fromPromise {
    F.delay {
      // Best guess: Firefox internally locks a ReadableStream after it is "drained"
      // This checks if the stream is locked before canceling it to avoid an error
      if (!rs.locked) exitCase match {
        case Resource.ExitCase.Succeeded =>
          rs.cancel(js.undefined)
        case Resource.ExitCase.Errored(ex) =>
          rs.cancel(ex.toString())
        case Resource.ExitCase.Canceled =>
          rs.cancel(js.undefined)
      }
      else js.Promise.resolve[Unit](())
    }
  }

  private final class Synchronizer[A] {

    type TakeCallback = Either[Throwable, A] => Unit
    type OfferCallback = Either[Throwable, TakeCallback] => Unit

    private[this] var callback: AnyRef = null
    @inline private[this] def offerCallback = callback.asInstanceOf[OfferCallback]
    @inline private[this] def takeCallback = callback.asInstanceOf[TakeCallback]

    def offer(cb: OfferCallback): Unit =
      if (callback ne null) {
        cb(Right(takeCallback))
        callback = null
      } else {
        callback = cb
      }

    def take(cb: TakeCallback): Unit =
      if (callback ne null) {
        offerCallback(Right(cb))
        callback = null
      } else {
        callback = cb
      }
  }

  private[dom] def toReadableStream[F[_]](in: Stream[F, Byte])(
      implicit F: Async[F]): Resource[F, ReadableStream[Uint8Array]] =
    Resource.eval(F.delay(new Synchronizer[Option[Uint8Array]])).flatMap { synchronizer =>
      val offers = in
        .chunks
        .noneTerminate
        .foreach { chunk =>
          F.async[Either[Throwable, Option[Uint8Array]] => Unit] { cb =>
            F.delay(synchronizer.offer(cb)).as(Some(F.unit))
          }.flatMap(cb => F.delay(cb(Right(chunk.map(_.toUint8Array)))))
        }
        .compile
        .drain

      offers.background.evalMap { _ =>
        F.delay {
          val source = new ReadableStreamUnderlyingSource[Uint8Array] {
            `type` = ReadableStreamType.bytes
            pull = js.defined { controller =>
              new js.Promise[Unit]({ (resolve, reject) =>
                synchronizer.take {
                  case Right(Some(bytes)) =>
                    controller.enqueue(bytes)
                    resolve(())
                    ()
                  case Right(None) =>
                    controller.close()
                    resolve(())
                    ()
                  case Left(ex) =>
                    reject(ex)
                    ()
                }
              })
            }
          }
          ReadableStream[Uint8Array](source)
        }
      }
    }

  private[dom] lazy val supportsRequestStreams = {
    val request = new DomRequest(
      "data:a/a;charset=utf-8,",
      new RequestInit {
        body = ReadableStream()
        method = HttpMethod.POST
        duplex = RequestDuplex.half
      }
    )

    val supportsStreamsInRequestObjects = !request.headers.has("Content-Type")

    if (!supportsStreamsInRequestObjects)
      js.Promise.resolve[Boolean](false)
    else
      Fetch
        .fetch(request)
        .`then`[Boolean](
          _ => true,
          (_ => false): js.Function1[Any, Boolean]
        )
  }

}
