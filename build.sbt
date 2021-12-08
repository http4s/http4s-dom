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

import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.scalajs.jsenv.selenium.SeleniumJSEnv

name := "http4s-dom"

ThisBuild / baseVersion := "0.2"

ThisBuild / organization := "org.http4s"
ThisBuild / organizationName := "http4s.org"
ThisBuild / publishGithubUser := "armanbilge"
ThisBuild / publishFullName := "Arman Bilge"

enablePlugins(SonatypeCiReleasePlugin)
ThisBuild / githubWorkflowTargetBranches := Seq("series/0.2")

ThisBuild / homepage := Some(url("https://github.com/http4s/http4s-dom"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/http4s/http4s-dom"),
    "https://github.com/http4s/http4s-dom.git"))

ThisBuild / crossScalaVersions := Seq("2.12.15", "3.1.0", "2.13.7")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"))

replaceCommandAlias(
  "ci",
  "; headerCheck; scalafmtSbtCheck; scalafmtCheck; clean; test; docs/mdoc; mimaReportBinaryIssues")

addCommandAlias("prePR", "; root/clean; scalafmtSbt; +root/scalafmtAll; +root/headerCreate")

val catsEffectVersion = "3.3.0"
val fs2Version = "3.2.3"
val http4sVersion = buildinfo.BuildInfo.http4sVersion // share version with build project
val scalaJSDomVersion = "2.0.0"
val circeVersion = "0.15.0-M1"
val munitVersion = "0.7.29"
val munitCEVersion = "1.0.7"

lazy val root =
  project.in(file(".")).aggregate(dom, chromeTests, firefoxTests).enablePlugins(NoPublishPlugin)

lazy val dom = project
  .in(file("dom"))
  .settings(
    name := "http4s-dom",
    description := "http4s browser integrations",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "co.fs2" %%% "fs2-io" % fs2Version,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.scala-js" %%% "scalajs-dom" % scalaJSDomVersion
    ),
    // TODO sbt-spiewak doesn't like sjs :(
    mimaPreviousArtifacts ~= { _.map(a => a.organization %% "http4s-dom_sjs1" % a.revision) }
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

def mkJSEnv(options: Capabilities) = {
  val config = SeleniumJSEnv
    .Config()
    .withMaterializeInServer(
      "target/selenium",
      s"http://localhost:$fileServicePort/target/selenium/")
  new SeleniumJSEnv(options, config)
}

lazy val chromeTests =
  tests.settings(Test / jsEnv := mkJSEnv(new ChromeOptions().setHeadless(true)))
lazy val firefoxTests =
  tests.settings(Test / jsEnv := mkJSEnv(new FirefoxOptions().setHeadless(true)))

lazy val fileServicePort = {
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

lazy val jsdocs =
  project
    .dependsOn(dom)
    .settings(
      fatalWarningsInCI := false, // Remove once mdocjs bumps to sjs-dom v2
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-circe" % http4sVersion,
        "io.circe" %%% "circe-generic" % "0.15.0-M1"
      )
    )
    .enablePlugins(ScalaJSPlugin)

import laika.ast.Path.Root
import laika.ast._
import laika.ast.LengthUnit._
import laika.helium.Helium
import laika.helium.config.{
  Favicon,
  HeliumIcon,
  IconLink,
  ImageLink,
  ReleaseInfo,
  Teaser,
  TextLink
}
import laika.theme.config.Color

Global / excludeLintKeys += laikaDescribe

lazy val docs =
  project
    .in(file("mdocs"))
    .settings(
      fatalWarningsInCI := false,
      mdocJS := Some(jsdocs),
      mdocVariables ++= Map(
        "js-opt" -> "fast",
        "HTTP4S_VERSION" -> http4sVersion,
        "CIRCE_VERSION" -> circeVersion
      ),
      Laika / sourceDirectories := Seq(mdocOut.value),
      laikaDescribe := "<disabled>",
      laikaConfig ~= { _.withRawContent },
      laikaExtensions ++= Seq(
        laika.markdown.github.GitHubFlavor,
        laika.parse.code.SyntaxHighlighting
      ),
      laikaTheme := Helium
        .defaults
        .all
        .metadata(
          language = Some("en"),
          title = Some("http4s-dom")
        )
        .site
        .autoLinkJS() // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
        .site
        .layout(
          contentWidth = px(860),
          navigationWidth = px(275),
          topBarHeight = px(35),
          defaultBlockSpacing = px(10),
          defaultLineHeight = 1.5,
          anchorPlacement = laika.helium.config.AnchorPlacement.Right
        )
        .site
        .themeColors(
          primary = Color.hex("5B7980"),
          secondary = Color.hex("cc6600"),
          primaryMedium = Color.hex("a7d4de"),
          primaryLight = Color.hex("e9f1f2"),
          text = Color.hex("5f5f5f"),
          background = Color.hex("ffffff"),
          bgGradient =
            (Color.hex("334044"), Color.hex("5B7980")) // only used for landing page background
        )
        .site
        .favIcons(
          Favicon
            .external("https://http4s.org/images/http4s-favicon.svg", "32x32", "image/svg+xml")
            .copy(sizes = None),
          Favicon.external("https://http4s.org/images/http4s-favicon.png", "32x32", "image/png")
        )
        .site
        .darkMode
        .disabled
        .site
        .topNavigationBar(
          homeLink = ImageLink.external(
            "https://http4s.org",
            Image.external("https://http4s.org/v1.0/images/http4s-logo-text-dark-2.svg")),
          navLinks = Seq(
            IconLink.external(
              "https://www.javadoc.io/doc/org.http4s/http4s-dom_sjs1_2.13/latest/org/http4s/dom/index.html",
              HeliumIcon.api,
              options = Styles("svg-link")),
            IconLink.external(
              "https://github.com/http4s/http4s-dom",
              HeliumIcon.github,
              options = Styles("svg-link")),
            IconLink.external("https://discord.gg/XF3CXcMzqD", HeliumIcon.chat),
            IconLink.external("https://twitter.com/http4s", HeliumIcon.twitter)
          )
        )
        .build
    )
    .enablePlugins(MdocPlugin, LaikaPlugin)

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    "site",
    "Publish Site",
    scalas = List(crossScalaVersions.value.last),
    cond = Some("github.event_name != 'pull_request'"),
    needs = List("build"),
    steps = githubWorkflowJobSetup.value.toList ::: List(
      WorkflowStep.Sbt(List("docs/mdoc", "docs/laikaSite"), name = Some("Generate")),
      WorkflowStep.Use(
        UseRef.Public("peaceiris", "actions-gh-pages", "v3"),
        Map(
          "github_token" -> "${{ secrets.GITHUB_TOKEN }}",
          "publish_dir" -> "./mdocs/target/docs/site",
          "publish_branch" -> "gh-pages"
        ),
        name = Some("Publish")
      )
    )
  )
