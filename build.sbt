import com.malliina.http.FullUrl
import com.malliina.sbtplay.PlayProject

import scala.sys.process.Process
import scala.util.Try
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._

val utilPlayDep = "com.malliina" %% "util-play" % "4.12.4"

val buildAndUpload = taskKey[FullUrl]("Uploads to S3")
val upFiles = taskKey[Seq[String]]("lists")

parallelExecution in ThisBuild := false

lazy val boatRoot = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(backend, frontend, agent, it)

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

lazy val agent = project.in(file("client"))
  .settings(clientSettings: _*)
  .dependsOn(crossJvm)
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)

lazy val it = Project("integration-tests", file("boat-test"))
  .settings(testSettings: _*)
  .dependsOn(backend, backend % "test->test", agent)

lazy val backendSettings = playSettings ++ Seq(
  libraryDependencies ++= Seq(
    //    "net.sf.marineapi" % "marineapi" % "0.13.0-SNAPSHOT",
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.h2database" % "h2" % "1.4.197",
    "org.mariadb.jdbc" % "mariadb-java-client" % "2.2.5",
    "com.zaxxer" % "HikariCP" % "3.2.0",
    "org.flywaydb" % "flyway-core" % "5.1.3",
    "org.apache.commons" % "commons-text" % "1.4",
    "com.malliina" %% "logstreams-client" % "1.0.0",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.358",
    utilPlayDep,
    utilPlayDep % Test classifier "tests"
  ),
  dependencyOverrides ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.8",
    "com.typesafe.akka" %% "akka-actor" % "2.5.8"
  ),
  routesImport ++= Seq(
    "com.malliina.boat.Bindables._",
    "com.malliina.boat.TrackName"
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
  name in Linux := "boat-agent",
  normalizedName := "boat-agent",
  maintainer := "Michael Skogberg",
  javaOptions in Universal ++= {
    val linuxName = (normalizedName in Debian).value
    Seq(
      s"-Dconf.dir=/usr/share/$linuxName/conf",
      s"-Dlogback.configurationFile=/etc/$linuxName/logback-prod.xml",
      s"-Dlog.dir=/var/run/$linuxName"
    )
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
    "com.typesafe.akka" %% "akka-http" % "10.1.1",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1",
    "com.lihaoyi" %% "scalatags" % "0.6.7",
    "commons-codec" % "commons-codec" % "1.11",
    "com.neuronrobotics" % "nrjavaserial" % "3.14.0",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  ),
  buildAndUpload := {
    val debFile = (packageBin in Debian).value
    val filename = S3Client.upload(debFile.toPath)
    val url = FullUrl("https", "boat.malliina.com", s"/files/$filename")
    streams.value.log.info(s"Uploaded package to '$url'.")
    url
  },
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
//    ReleaseStep(action = releaseStepCommand("buildAndUpload")),
//    publishArtifacts, // : ReleaseStep, checks whether `publishTo` is properly set up
    setNextVersion,
    commitNextVersion,
    pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
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
  scalaVersion := "2.12.6",
  scalacOptions := Seq("-unchecked", "-deprecation"),
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("malliina", "maven"),
    Resolver.mavenLocal
  ),
  dependencyOverrides ++= Seq(
    "com.typesafe.akka" %% "akka-http-core" % "10.1.1",
    "com.typesafe.akka" %% "akka-parsing" % "10.1.1"
  )
)

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")
