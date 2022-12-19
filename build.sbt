name := """dwc"""
organization := "de.doomwarriors"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  jdbc, javaJdbc, "org.postgresql" % "postgresql" % "42.5.1",
  guice,
  ehcache,
  evolutions,
  javaWs,
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.h2database" % "h2" % "2.1.214"
)