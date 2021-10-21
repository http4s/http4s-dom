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

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.scalajs.jsenv.selenium.SeleniumJSEnv

import JSEnv._

name := "http4s-dom"

ThisBuild / baseVersion := "0.1"

ThisBuild / organization := "org.http4s"
ThisBuild / organizationName := "http4s.org"
ThisBuild / publishGithubUser := "armanbilge"
ThisBuild / publishFullName := "Arman Bilge"

enablePlugins(SonatypeCiReleasePlugin)
ThisBuild / githubWorkflowTargetBranches := Seq("series/0.1")

ThisBuild / homepage := Some(url("https://github.com/http4s/http4s-dom"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/http4s/http4s-dom"),
    "https://github.com/http4s/http4s-dom.git"))

ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.6")

replaceCommandAlias("ci", CI.AllCIs.map(_.toString).mkString)
addCommandAlias("ciFirefox", CI.Firefox.toString)
addCommandAlias("ciChrome", CI.Chrome.toString)

addCommandAlias("prePR", "; root/clean; scalafmtSbt; +root/scalafmtAll; +root/headerCreate")

lazy val useJSEnv = settingKey[JSEnv]("Browser for running Scala.js tests")
Global / useJSEnv := Chrome

lazy val fileServicePort = settingKey[Int]("Port for static file server")
Global / fileServicePort := {
  import cats.data.Kleisli
  import cats.effect.IO
  import cats.effect.unsafe.implicits.global
  import com.comcast.ip4s.Port
  import org.http4s.ember.server.EmberServerBuilder
  import org.http4s.server.staticcontent._

  (for {
    deferredPort <- IO.deferred[Int]
    _ <- EmberServerBuilder
      .default[IO]
      .withPort(Port.fromInt(0).get)
      .withHttpApp {
        Kleisli { req =>
          fileService[IO](FileService.Config(".")).orNotFound.run(req).map { res =>
            // TODO find out why mime type is not auto-inferred
            if (req.uri.renderString.endsWith(".js"))
              res.withHeaders(
                "Service-Worker-Allowed" -> "/",
                "Content-Type" -> "text/javascript"
              )
            else res
          }
        }
      }
      .build
      .map(_.address.getPort)
      .evalTap(deferredPort.complete(_))
      .useForever
      .start
    port <- deferredPort.get
  } yield port).unsafeRunSync()
}

ThisBuild / Test / jsEnv := {
  val config = SeleniumJSEnv
    .Config()
    .withMaterializeInServer(
      "target/selenium",
      s"http://localhost:${fileServicePort.value}/target/selenium/")

  useJSEnv.value match {
    case Chrome =>
      val options = new ChromeOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, config)
    case Firefox =>
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, config)
  }
}

val catsEffectVersion = "3.2.9"
val fs2Version = "3.1.6"
val http4sVersion = "0.23.6"
val scalaJSDomVersion = "2.0.0"
val munitVersion = "0.7.29"
val munitCEVersion = "1.0.6"

lazy val root =
  project.in(file(".")).aggregate(dom, tests).enablePlugins(NoPublishPlugin)

lazy val dom = project
  .in(file("dom"))
  .settings(
    name := "http4s-dom",
    description := "http4s browser integrations",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.scala-js" %%% "scalajs-dom" % scalaJSDomVersion
    )
  )
  .enablePlugins(ScalaJSPlugin)

lazy val tests = project
  .in(file("tests"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    (Test / test) := (Test / test).dependsOn(Compile / fastOptJS).value,
    buildInfoKeys := Seq[BuildInfoKey](scalaVersion),
    buildInfoPackage := "org.http4s.dom",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % munitCEVersion % Test
    )
  )
  .dependsOn(dom)
  .enablePlugins(ScalaJSPlugin, BuildInfoPlugin, NoPublishPlugin)
