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

ThisBuild / tlBaseVersion := "0.2"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)
ThisBuild / startYear := Some(2021)

ThisBuild / githubWorkflowTargetBranches := Seq("series/0.2")
ThisBuild / tlCiReleaseBranches := Seq("series/0.2")
ThisBuild / tlSitePublishBranch := Some("series/0.2")

ThisBuild / crossScalaVersions := Seq("2.12.15", "3.1.2", "2.13.8")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"))
ThisBuild / githubWorkflowBuildMatrixAdditions += "browser" -> List("Chrome", "Firefox")
ThisBuild / githubWorkflowBuildSbtStepPreamble += s"set Global / useJSEnv := JSEnv.$${{ matrix.browser }}"
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- (ThisBuild / crossScalaVersions).value.init
  } yield MatrixExclude(Map("scala" -> scala, "browser" -> "Firefox"))
}

lazy val useJSEnv = settingKey[JSEnv]("Browser for running Scala.js tests")
Global / useJSEnv := Chrome

lazy val fileServicePort = settingKey[Int]("Port for static file server")
Global / fileServicePort := {
  import cats.data.Kleisli
  import cats.effect.IO
  import cats.effect.unsafe.implicits.global
  import org.http4s._
  import org.http4s.dsl.io._
  import org.http4s.blaze.server.BlazeServerBuilder
  import org.http4s.server.staticcontent._
  import java.net.InetSocketAddress

  (for {
    deferredPort <- IO.deferred[Int]
    _ <- BlazeServerBuilder[IO]
      .bindSocketAddress(new InetSocketAddress("localhost", 0))
      .withHttpWebSocketApp { wsb =>
        HttpRoutes
          .of[IO] {
            case Method.GET -> Root / "ws" =>
              wsb.build(identity)
            case req =>
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
          .orNotFound
      }
      .resource
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

val catsEffectVersion = "3.3.12"
val fs2Version = "3.2.7"
val http4sVersion = "0.23.11-506-d570136-SNAPSHOT"
// val http4sVersion = buildinfo.BuildInfo.http4sVersion // share version with build project
val scalaJSDomVersion = "2.2.0"
val circeVersion = "0.15.0-M1"
val munitVersion = "0.7.29"
val munitCEVersion = "1.0.7"

ThisBuild / resolvers +=
  "s01 snapshots".at("https://s01.oss.sonatype.org/content/repositories/snapshots/")

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
    buildInfoKeys := Seq[BuildInfoKey](
      fileServicePort,
      BuildInfoKey("runtime" -> useJSEnv.value.toString),
      BuildInfoKey(
        "workerDir" -> (Compile / fastLinkJS / scalaJSLinkerOutputDirectory)
          .value
          .relativeTo((ThisBuild / baseDirectory).value)
          .get
          .toString
      )
    ),
    buildInfoPackage := "org.http4s.dom",
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-client-testkit" % http4sVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % munitCEVersion % Test
    )
  )
  .dependsOn(dom)
  .enablePlugins(ScalaJSPlugin, BuildInfoPlugin, NoPublishPlugin)

lazy val jsdocs =
  project
    .dependsOn(dom)
    .settings(
      tlFatalWarningsInCi := false,
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-circe" % http4sVersion,
        "io.circe" %%% "circe-generic" % circeVersion
      )
    )
    .enablePlugins(ScalaJSPlugin)

lazy val docs = project
  .in(file("site"))
  .settings(
    tlSiteApiModule := Some((dom / projectID).value),
    tlSiteApiPackage := Some("org.http4s.dom"),
    tlFatalWarningsInCi := false,
    mdocJS := Some(jsdocs),
    tlSiteRelatedProjects ++= Seq(
      "calico" -> url("https://armanbilge.github.io/calico/")
    ),
    mdocVariables ++= Map(
      "js-opt" -> "fast",
      "HTTP4S_VERSION" -> http4sVersion,
      "CIRCE_VERSION" -> circeVersion
    ),
    laikaConfig := {
      import laika.rewrite.link._
      laikaConfig
        .value
        .withRawContent
        .withConfigValue(LinkConfig(apiLinks = List(
          ApiLinks(
            baseUri =
              s"https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/${mdocVariables.value("VERSION")}/",
            packagePrefix = "org.http4s.dom"),
          ApiLinks(
            baseUri = s"https://www.javadoc.io/doc/org.http4s/http4s-docs_2.13/$http4sVersion/",
            packagePrefix = "org.http4s"
          )
        )))
    },
    tlSiteHeliumConfig ~= {
      // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
      _.site.autoLinkJS()
    }
  )
  .enablePlugins(Http4sOrgSitePlugin)
