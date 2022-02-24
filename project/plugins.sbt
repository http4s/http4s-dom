val http4sVersion = "0.23.10"

enablePlugins(BuildInfoPlugin)
buildInfoKeys += "http4sVersion" -> http4sVersion

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"
libraryDependencies += "org.http4s" %% "http4s-ember-server" % http4sVersion

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.http4s" % "sbt-http4s-org" % "0.11.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.9.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.0+33-9014e2d7-SNAPSHOT")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
