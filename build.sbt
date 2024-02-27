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

name := "http4s-dom"

ThisBuild / tlBaseVersion := "0.2"
ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)
ThisBuild / startYear := Some(2021)

ThisBuild / tlCiReleaseBranches := Seq("series/0.2")
ThisBuild / tlSitePublishBranch := Some("series/0.2")

val scala213 = "2.13.13"
ThisBuild / crossScalaVersions := Seq("2.12.18", scala213, "3.3.1")
ThisBuild / scalaVersion := scala213

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / tlJdkRelease := Some(8)

ThisBuild / githubWorkflowBuildMatrixAdditions +=
  "project" -> List("rootNodeJS", "rootChrome", "rootFirefox")
ThisBuild / githubWorkflowBuildSbtStepPreamble += s"project $${{ matrix.project }}"

ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v3"),
    name = Some("Setup Node.js"),
    params = Map("node-version" -> "18"),
    cond = Some("matrix.project == 'rootNodeJS'")
  )

lazy val fileServicePort = settingKey[Int]("Port for static file server")
Global / fileServicePort := {
  import cats.data.Kleisli
  import cats.effect.IO
  import cats.effect.unsafe.implicits.global
  import com.comcast.ip4s._
  import fs2.Stream
  import org.http4s._
  import org.http4s.websocket._
  import org.http4s.dsl.io._
  import org.http4s.ember.server.EmberServerBuilder
  import org.http4s.server.staticcontent._
  import java.net.InetSocketAddress
  import scala.concurrent.duration._

  (for {
    deferredPort <- IO.deferred[Int]
    _ <- EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp { wsb =>
        HttpRoutes
          .of[IO] {
            case Method.GET -> Root / "ws" =>
              wsb.build(identity)
            case Method.GET -> Root / "slows" =>
              IO.sleep(3.seconds) *> wsb.build(identity)
            case Method.GET -> Root / "hello-goodbye" =>
              wsb.build(in => Stream(WebSocketFrame.Text("hello")).concurrently(in.drain))
            case req =>
              fileService[IO](FileService.Config[IO](".")).orNotFound.run(req).map { res =>
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
      .build
      .map(_.address.getPort)
      .evalTap(deferredPort.complete(_))
      .useForever
      .start
    port <- deferredPort.get
  } yield port).unsafeRunSync()
}

val catsEffectVersion = "3.5.2"
val fs2Version = "3.9.3"
val http4sVersion = buildinfo.BuildInfo.http4sVersion // share version with build project
val scalaJSDomVersion = "2.8.0"
val circeVersion = "0.14.2"
val munitVersion = "1.0.0-M10"
val munitCEVersion = "2.0.0-M4"

lazy val root = project
  .in(file("."))
  .aggregate(dom, rootNodeJS, rootChrome, rootFirefox)
  .enablePlugins(NoPublishPlugin)

lazy val rootNodeJS =
  project.in(file(".rootNodeJS")).aggregate(dom, testsNodeJS).enablePlugins(NoPublishPlugin)

lazy val rootChrome =
  project.in(file(".rootChrome")).aggregate(dom, testsChrome).enablePlugins(NoPublishPlugin)

lazy val rootFirefox =
  project.in(file(".rootFirefox")).aggregate(dom, testsFirefox).enablePlugins(NoPublishPlugin)

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
    ),
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      Seq(
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "org.http4s.dom.FetchClientBuilder.this"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("org.http4s.dom.FetchOptions.*"),
        ProblemFilters.exclude[Problem]("org.http4s.dom.FetchOptions#FetchOptionsImpl*"),
        ProblemFilters.exclude[Problem]("org.http4s.dom.FetchOptions$FetchOptionsImpl*")
      ) ++ {
        if (tlIsScala3.value)
          Seq(
            ProblemFilters.exclude[DirectMissingMethodProblem](
              "org.http4s.dom.package.closeReadableStream"),
            ProblemFilters.exclude[DirectMissingMethodProblem](
              "org.http4s.dom.package.fromReadableStream"),
            ProblemFilters.exclude[DirectMissingMethodProblem](
              "org.http4s.dom.package.toDomHeaders")
          )
        else Seq()
      }
    }
  )
  .enablePlugins(ScalaJSPlugin)

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.jsenv.selenium.SeleniumJSEnv

lazy val seleniumConfig = Def.setting {
  SeleniumJSEnv
    .Config()
    .withMaterializeInServer(
      "target/selenium",
      s"http://localhost:${fileServicePort.value}/target/selenium/")
}

def configureTest(project: Project): Project =
  project
    .dependsOn(dom)
    .enablePlugins(ScalaJSPlugin, NoPublishPlugin)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-client-testkit" % http4sVersion,
        "org.scalameta" %%% "munit" % munitVersion % Test,
        "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % Test,
        "org.typelevel" %%% "scalacheck-effect-munit" % "2.0.0-M2" % Test
      ),
      Compile / unmanagedSourceDirectories +=
        (LocalRootProject / baseDirectory).value / "tests" / "src" / "main" / "scala",
      Test / unmanagedSourceDirectories +=
        (LocalRootProject / baseDirectory).value / "tests" / "src" / "test" / "scala",
      testOptions += Tests.Argument("+l")
    )

def configureBrowserTest(project: Project): Project =
  project
    .enablePlugins(BuildInfoPlugin)
    .settings(
      Compile / unmanagedSourceDirectories +=
        (LocalRootProject / baseDirectory).value / "testsBrowser" / "src" / "main" / "scala",
      Test / unmanagedSourceDirectories +=
        (LocalRootProject / baseDirectory).value / "testsBrowser" / "src" / "test" / "scala",
      Compile / scalaJSUseMainModuleInitializer := true,
      Test / test := (Test / test).dependsOn(Compile / fastOptJS).value,
      buildInfoPackage := "org.http4s.dom",
      buildInfoKeys := Seq[BuildInfoKey](
        fileServicePort,
        BuildInfoKey(
          "workerDir" -> (Compile / fastLinkJS / scalaJSLinkerOutputDirectory)
            .value
            .relativeTo((ThisBuild / baseDirectory).value)
            .get
            .toString
        )
      )
    )

lazy val testsNodeJS = project
  .configure(configureTest)
  .settings(
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val testsChrome = project
  .configure(configureTest, configureBrowserTest)
  .settings(
    jsEnv := {
      val options = new ChromeOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, seleniumConfig.value)
    }
  )

lazy val testsFirefox = project
  .configure(configureTest, configureBrowserTest)
  .settings(
    jsEnv := {
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, seleniumConfig.value)
    }
  )

lazy val artifactSizeTest = project
  .in(file("artifact-size-test"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-circe" % http4sVersion
    ),
    bundleMonCheckRun := true,
    bundleMonCommitStatus := false,
    bundleMonPrComment := false
  )
  .dependsOn(dom)
  .enablePlugins(BundleMonPlugin, NoPublishPlugin)

ThisBuild / githubWorkflowBuild +=
  WorkflowStep.Sbt(
    List("artifactSizeTest/bundleMon"),
    name = Some("Monitor artifact size"),
    cond = Some("matrix.project == 'rootNodeJS'")
  )

lazy val jsdocs =
  project
    .dependsOn(dom)
    .settings(
      tlFatalWarnings := false,
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
    tlFatalWarnings := false,
    mdocJS := Some(jsdocs),
    mdocVariables ++= Map(
      "HTTP4S_VERSION" -> http4sVersion,
      "CIRCE_VERSION" -> circeVersion
    ),
    laikaConfig := {
      import laika.config._
      laikaConfig
        .value
        .withRawContent
        .withConfigValue(
          LinkConfig
            .empty
            .addApiLinks(
              ApiLinks(
                s"https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/${mdocVariables.value("VERSION")}/")
                .withPackagePrefix("org.http4s.dom"),
              ApiLinks(
                s"https://www.javadoc.io/doc/org.http4s/http4s-docs_2.13/$http4sVersion/")
                .withPackagePrefix("org.http4s")
            )
        )
    }
  )
  .enablePlugins(Http4sOrgSitePlugin)
