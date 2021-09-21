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
ThisBuild / spiewakCiReleaseSnapshots := false
ThisBuild / spiewakMainBranches := Seq("main")

ThisBuild / homepage := Some(url("https://github.com/http4s/http4s-dom"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/http4s/http4s-dom"),
    "https://github.com/http4s/http4s-dom.git"))

ThisBuild / crossScalaVersions := Seq( /*"3.0.2",*/ "2.12.15", "2.13.6")

replaceCommandAlias("ci", CI.AllCIs.map(_.toString).mkString)
addCommandAlias("ciFirefox", CI.Firefox.toString)
addCommandAlias("ciChrome", CI.Chrome.toString)

addCommandAlias("prePR", "; root/clean; scalafmtSbt; +root/scalafmtAll; +root/headerCreate")

ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.Run(
    List("./scripts/static_server.py &"),
    name = Some("Start static file server"))

lazy val useJSEnv = settingKey[JSEnv]("Browser for running Scala.js tests")
Global / useJSEnv := Chrome

ThisBuild / Test / jsEnv := {
  val config = SeleniumJSEnv
    .Config()
    .withMaterializeInServer("target/selenium", "http://localhost:8888/target/selenium/")

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
val fs2Version = "3.1.2"
val http4sVersion = "1.0.0-M25"
val scalaJSDomVersion = "1.2.0"
val munitVersion = "0.7.29"
val munitCEVersion = "1.0.5"

lazy val root =
  project
    .in(file("."))
    .aggregate(core, fetchClient, serviceWorker, tests)
    .enablePlugins(NoPublishPlugin)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "http4s-dom-core",
    description := "Base library for dom http4s client and apps",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.scala-js" %%% "scalajs-dom" % scalaJSDomVersion
    )
  )
  .enablePlugins(ScalaJSPlugin)

lazy val fetchClient = project
  .in(file("fetch-client"))
  .settings(
    name := "http4s-dom-fetch-client",
    description := "browser fetch implementation for http4s clients",
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-client" % http4sVersion
    )
  )
  .dependsOn(core)
  .enablePlugins(ScalaJSPlugin)

lazy val serviceWorker = project
  .in(file("service-worker"))
  .settings(
    name := "http4s-dom-fetch-client",
    description := "browser service worker implementation for http4s apps",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion
    )
  )
  .dependsOn(core)
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
  .dependsOn(serviceWorker, fetchClient % Test)
  .enablePlugins(ScalaJSPlugin, BuildInfoPlugin, NoPublishPlugin)
