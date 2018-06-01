import com.malliina.sbtplay.PlayProject

import scala.sys.process.Process
import scala.util.Try

val utilPlayDep = "com.malliina" %% "util-play" % "4.12.2"

parallelExecution in ThisBuild := false

lazy val boatRoot = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(backend, frontend, client, it)

lazy val backend = PlayProject.linux("boat", file("backend"))
  .settings(backendSettings: _*)
  .dependsOn(crossJvm)

lazy val frontend = project.in(file("frontend"))
  .settings(frontendSettings: _*)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(crossJs)

lazy val cross = crossProject.in(file("shared"))
  .settings(sharedSettings: _*)

lazy val crossJvm = cross.jvm
lazy val crossJs = cross.js

lazy val client = project.in(file("client"))
  .settings(clientSettings: _*)
  .dependsOn(crossJvm)
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)

lazy val it = Project("integration-tests", file("boat-test"))
  .settings(testSettings: _*)
  .dependsOn(backend, backend % "test->test", client)

lazy val backendSettings = playSettings ++ Seq(
  libraryDependencies ++= Seq(
//    "net.sf.marineapi" % "marineapi" % "0.13.0-SNAPSHOT",
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.h2database" % "h2" % "1.4.197",
    "org.mariadb.jdbc" % "mariadb-java-client" % "2.2.3",
    "com.zaxxer" % "HikariCP" % "3.1.0",
    "org.apache.commons" % "commons-text" % "1.3",
    "com.malliina" %% "logstreams-client" % "1.0.0",
    utilPlayDep,
    utilPlayDep % Test classifier "tests"
  ),
  dependencyOverrides ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.8",
    "com.typesafe.akka" %% "akka-actor" % "2.5.8"
  ),
  pipelineStages := Seq(digest, gzip),
  scalaJSProjects := Seq(frontend),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "gitHash" -> gitHash),
  buildInfoPackage := "com.malliina.boat",
  // linux packaging
  httpPort in Linux := Option("8465"),
  httpsPort in Linux := Option("disabled"),
  maintainer := "Michael Skogberg <malliina123@gmail.com>",
  // WTF?
  linuxPackageSymlinks := linuxPackageSymlinks.value.filterNot(_.link == "/usr/bin/starter"),
  javaOptions in Universal ++= {
    val linuxName = (name in Linux).value
    Seq(
      s"-Dconfig.file=/etc/$linuxName/production.conf",
      s"-Dlogger.file=/etc/$linuxName/logback-prod.xml"
    )
  }
)

lazy val frontendSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.6",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.3",
    "org.scalatest" %%% "scalatest" % "3.0.4" % Test
  ),
  scalaJSUseMainModuleInitializer := true
)

lazy val sharedSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.typesafe.play" %%% "play-json" % "2.6.9",
    "com.malliina" %%% "primitives" % "1.5.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.7"
  )
)

lazy val clientSettings = commonSettings ++ Seq(
  normalizedName := "boat-agent",
  maintainer := "Michael Skogberg",
  javaOptions in Universal ++= {
    val linuxName = (normalizedName in Debian).value
    Seq(s"-Dconf.dir=/usr/share/$linuxName/conf")
  },
  linuxPackageMappings += {
    val linuxName = (normalizedName in Debian).value
    packageTemplateMapping(s"/usr/share/$linuxName/conf")().withUser(daemonUser.value).withGroup(daemonUser.value)
  },
  libraryDependencies ++= Seq(
    "com.malliina" %% "primitives" % "1.5.2",
    "com.neovisionaries" % "nv-websocket-client" % "2.4",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "com.malliina" %% "logback-rx" % "1.2.0",
    "com.typesafe.akka" %% "akka-stream" % "2.5.12",
    "com.typesafe.akka" %% "akka-http"   % "10.1.1",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1",
    "com.lihaoyi" %% "scalatags" % "0.6.7",
    "commons-codec" % "commons-codec" % "1.11",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )
)

lazy val testSettings = playSettings ++ Seq(
  libraryDependencies ++= Seq(
    utilPlayDep % Test classifier "tests"
  )
)

lazy val playSettings = commonSettings ++ Seq(
  dependencyOverrides ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.8",
    "com.typesafe.akka" %% "akka-actor" % "2.5.8"
  )
)

lazy val commonSettings = Seq(
  organization := "com.malliina",
  version := "0.0.2",
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-unchecked", "-deprecation"),
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("malliina", "maven"),
    Resolver.mavenLocal
  )
)

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")
