lazy val `akka-accessor` = (project in file("."))
  .settings(
    name := "akka-accessor",
    organization := "io.github.junheng",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.0.0",
      "org.json4s" %% "json4s-jackson" % "3.2.11",
      "org.json4s" %% "json4s-ext" % "3.2.11",
      "com.typesafe" % "config" % "1.3.1"
    )
  )
