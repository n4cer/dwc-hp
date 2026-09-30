name := """dwc"""
organization := "de.doomwarriors"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  jdbc, javaJdbc, "org.postgresql" % "postgresql" % "42.7.13",
  guice,
  ehcache,
  evolutions,
  javaWs,
  "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20260924.2",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.h2database" % "h2" % "2.5.252"
)
