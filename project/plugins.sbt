val http4sVersion = "0.23.30"

enablePlugins(BuildInfoPlugin)
buildInfoKeys += "http4sVersion" -> http4sVersion

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"
libraryDependencies += "org.http4s" %% "http4s-dsl" % http4sVersion
libraryDependencies += "org.http4s" %% "http4s-ember-server" % http4sVersion

addSbtPlugin("org.http4s" % "sbt-http4s-org" % "2.0.0")
addSbtPlugin("com.armanbilge" % "sbt-bundlemon" % "0.1.4")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.7.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
