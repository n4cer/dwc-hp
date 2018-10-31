name := """dwc"""
organization := "de.doomwarriors"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  jdbc, javaJdbc, "org.postgresql" % "postgresql" % "42.1.4",
  guice,
  ehcache,
  evolutions,
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.h2database" % "h2" % "1.4.192"
)