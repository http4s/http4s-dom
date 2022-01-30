val http4sVersion = "0.23.9"

enablePlugins(BuildInfoPlugin)
buildInfoKeys += "http4sVersion" -> http4sVersion

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"
libraryDependencies += "org.http4s" %% "http4s-dsl" % http4sVersion
libraryDependencies += "org.http4s" %% "http4s-ember-server" % http4sVersion
libraryDependencies += "org.http4s" %% "http4s-blaze-server" % http4sVersion

addSbtPlugin("org.http4s" % "sbt-http4s-org" % "0.11.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
