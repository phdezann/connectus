name := """connectus"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.google.apis" % "google-api-services-gmail" % "v1-rev36-1.21.0",
  "com.google.api-client" % "google-api-client" % "1.21.0",
  "javax.mail" % "mail" % "1.4.7",
  "com.firebase" % "firebase-client-jvm" % "2.5.2",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.11",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  cache,
  ws,
  specs2 % Test
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)
