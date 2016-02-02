name := """play-getting-started"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.google.apis" % "google-api-services-gmail" % "v1-rev36-1.21.0",
  "com.google.api-client" % "google-api-client" % "1.21.0",
  "javax.mail" % "mail" % "1.4.7",
  "com.firebase" % "firebase-client-jvm" % "2.5.0",
  cache,
  ws,
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.5.4",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.4",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev36-1.21.0",
  "com.google.code.findbugs" % "jsr305" % "2.0.3",
  "com.google.guava" % "guava" % "19.0",
  "com.typesafe" % "config" % "1.3.0",
  "commons-codec" % "commons-codec" % "1.10",
  "commons-logging" % "commons-logging" % "1.1.3",
  "io.netty" % "netty" % "3.10.4.Final",
  "junit" % "junit" % "4.12",
  "org.apache.httpcomponents" % "httpclient" % "4.3.4",
  "org.apache.httpcomponents" % "httpcore" % "4.3.2",
  "org.apache.httpcomponents" % "httpcore" % "4.3.2",
  "org.scala-lang" % "scala-library" % "2.11.7",
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
  "org.slf4j" % "slf4j-api" % "1.7.10",
  "xalan" % "serializer" % "2.7.2"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)
