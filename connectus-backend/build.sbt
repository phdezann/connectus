name := """connectus"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

pipelineStages := Seq(gzip)

libraryDependencies ++= Seq(
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "requirejs" % "2.1.19",
  "org.webjars" % "jquery" % "2.1.4",
  "org.webjars" % "jquery-easing" % "1.3",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "font-awesome" % "4.3.0",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev36-1.21.0",
  "com.google.api-client" % "google-api-client" % "1.21.0",
  "javax.mail" % "mail" % "1.4.7",
  "com.firebase" % "firebase-client-jvm" % "2.5.2",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.11",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  cache,
  filters,
  ws,
  specs2 % Test
)

includeFilter in(Assets, LessKeys.less) := "*.less"

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)
