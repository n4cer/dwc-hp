name := """dwc"""
organization := "de.doomwarriors"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  jdbc, javaJdbc, "org.postgresql" % "postgresql" % "42.7.8",
  guice,
  ehcache,
  evolutions,
  javaWs,
  "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20240325.1",
  "com.h2database" % "h2" % "2.3.232"
)
