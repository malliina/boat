import com.malliina.http.FullUrl
import com.malliina.sbtplay.PlayProject
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._

import scala.sys.process.Process
import scala.util.Try

val utilPlayVersion = "4.18.1"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion
val primitiveVersion = "1.7.1"
val akkaVersion = "2.5.17"
val buildAndUpload = taskKey[FullUrl]("Uploads to S3")
val upFiles = taskKey[Seq[String]]("lists")
val bootClasspath = taskKey[String]("bootClasspath")

parallelExecution in ThisBuild := false

lazy val boatRoot = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(backend, frontend, agent, it, utils)

lazy val backend = PlayProject.linux("boat", file("backend"))
  .enablePlugins(WebScalaJSBundlerPlugin)
  .settings(backendSettings: _*)
  .dependsOn(crossJvm)

lazy val frontend = project.in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb)
  .dependsOn(crossJs)
  .settings(frontendSettings: _*)

lazy val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
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

lazy val utils = project.in(file("utils"))
  .settings(utilsSettings: _*)

lazy val backendSettings = playSettings ++ Seq(
  unmanagedResourceDirectories in Compile += baseDirectory.value / "docs",
  libraryDependencies ++= Seq(
    //    "net.sf.marineapi" % "marineapi" % "0.13.0-SNAPSHOT",
    "com.vividsolutions" % "jts" % "1.13",
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.h2database" % "h2" % "1.4.196",
    "org.orbisgis" % "h2gis" % "1.4.0",
    "mysql" % "mysql-connector-java" % "5.1.47",
    "com.zaxxer" % "HikariCP" % "3.2.0",
    "org.flywaydb" % "flyway-core" % "5.2.4",
    "org.apache.commons" % "commons-text" % "1.6",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.466",
    "com.vladsch.flexmark" % "flexmark-html-parser" % "0.34.58",
    "com.malliina" %% "logstreams-client" % "1.4.0",
    "com.malliina" %% "play-social" % utilPlayVersion,
    "com.malliina" %% "mobile-push" % "1.16.0",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    utilPlayDep,
    utilPlayDep % Test classifier "tests"
  ),
  routesImport ++= Seq(
    "com.malliina.boat.Bindables._",
    "com.malliina.boat.TrackName",
    "com.malliina.boat.BoatName"
  ),
  pipelineStages := Seq(digest, gzip),
  scalaJSProjects := Seq(frontend),
  pipelineStages in Assets := Seq(scalaJSPipeline),
//  npmAssets ++= NpmAssets.ofProject(client) { modules => (modules / "font-awesome").allPaths }.value,
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
      s"-Dlogger.file=/etc/$linuxName/logback-prod.xml",
      s"-J-Xbootclasspath/p:${bootClasspath.value}"
    )
  },
  bootClasspath := {
    val alpnFile = scriptClasspathOrdering.value
      .map { case (_, dest) => dest }
      .find(_.contains("alpn-boot"))
      .getOrElse(sys.error("Unable to find alpn-boot"))
    val name = (packageName in Debian).value
    val installLocation = defaultLinuxInstallLocation.value
    s"$installLocation/$name/$alpnFile"
  },
  javaOptions in Test ++= {
    val attList = (managedClasspath in Runtime).value
    for {
      file <- attList.map(_.data)
      path = file.getAbsolutePath
      if path.contains("alpn-boot")
    } yield {
      s"-Xbootclasspath/p:$path"
    }
  }
)

lazy val frontendSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.6",
    "org.scalatest" %%% "scalatest" % "3.0.5" % Test
  ),
  npmDependencies in Compile ++= Seq(
    "@turf/turf" -> "5.1.6",
    "mapbox-gl" -> "0.52.0",
    "chart.js" -> "2.7.3"
//    "@fortawesome/fontawesome-free" -> "5.6.3"
  ),
  version in webpack := "4.27.1",
  emitSourceMaps := false,
  scalaJSUseMainModuleInitializer := true,
  webpackBundlingMode := BundlingMode.LibraryOnly()
)

lazy val sharedSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.typesafe.play" %%% "play-json" % "2.6.10",
    "com.malliina" %%% "primitives" % primitiveVersion,
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "org.scalatest" %%% "scalatest" % "3.0.5" % Test
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
      s"-Dlogger.file=/etc/$linuxName/logback-prod.xml",
      s"-Dlog.dir=/var/run/$linuxName"
    )
  },
  linuxPackageMappings += {
    val linuxName = (normalizedName in Debian).value
    packageTemplateMapping(s"/usr/share/$linuxName/conf")().withUser(daemonUser.value).withGroup(daemonUser.value)
  },
  libraryDependencies ++= Seq(
    "com.malliina" %% "primitives" % primitiveVersion,
    "com.malliina" %% "logback-rx" % "1.4.0",
    "com.neovisionaries" % "nv-websocket-client" % "2.6",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "10.1.5",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5",
    "com.lihaoyi" %% "scalatags" % "0.6.7",
    "commons-codec" % "commons-codec" % "1.11",
    "com.neuronrobotics" % "nrjavaserial" % "3.14.0",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  ),
  buildAndUpload := {
    val debFile = (packageBin in Debian).value
    val filename = S3Client.upload(debFile.toPath)
    val url = FullUrl("https", "www.boat-tracker.com", s"/files/$filename")
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

lazy val utilsSettings = basicSettings ++ Seq(
  resolvers += "GeoTools" at "https://download.osgeo.org/webdav/geotools/",
  libraryDependencies ++= Seq(
    "javax.media" % "jai_core" % "1.1.3" from "https://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
    "org.geotools" % "gt-shapefile" % "20.0" exclude("javax.media", "jai_core"),
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )
)

lazy val testSettings = playSettings ++ Seq(
  libraryDependencies ++= Seq(
    utilPlayDep % Test classifier "tests"
  )
)

lazy val playSettings = commonSettings

lazy val commonSettings = basicSettings ++ Seq(
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("malliina", "maven"),
    Resolver.mavenLocal
  )
)

lazy val basicSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.12.8",
  scalacOptions := Seq("-unchecked", "-deprecation")
)

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")
