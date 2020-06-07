import com.malliina.http.FullUrl
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.packager.docker.DockerVersion

import scala.sys.process.Process
import scala.util.Try

val mapboxVersion = "1.10.1"
val utilPlayVersion = "5.11.0"
val munitVersion = "0.7.8"
val testContainersScalaVersion = "0.37.0"
val scalaTagsVersion = "0.9.1"
val primitiveVersion = "1.17.0"
val akkaVersion = "2.6.5"
val akkaHttpVersion = "10.1.12"
val playJsonVersion = "2.9.0"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion
val utilPlayTestDep = utilPlayDep % Test classifier "tests"
val munitDep = "org.scalameta" %% "munit" % munitVersion % Test
val buildAndUpload = taskKey[FullUrl]("Uploads to S3")
val upFiles = taskKey[Seq[String]]("lists")
val deployDocs = taskKey[Unit]("Deploys documentation")
val prodPort = 9000

parallelExecution in ThisBuild := false
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

val basicSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.13.2",
  scalacOptions := Seq("-unchecked", "-deprecation")
)

val boatSettings = Seq(
  version := "1.2.0"
)

val commonSettings = basicSettings ++ Seq(
  deployDocs := Process("mkdocs gh-deploy").run(streams.value.log).exitValue(),
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in packageDoc := false,
  sources in (Compile, doc) := Seq.empty
)

val jvmSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    munitDep
  ),
  testFrameworks += new TestFramework("munit.Framework")
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .disablePlugins(RevolverPlugin)
  .settings(commonSettings ++ boatSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.malliina" %%% "primitives" % primitiveVersion,
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, NodeJsPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings ++ boatSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "org.scala-js" %%% "scalajs-dom" % "1.0.0",
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.13.0",
      "@mapbox/mapbox-gl-geocoder" -> "4.5.1",
      "@turf/turf" -> "5.1.6",
      "bootstrap" -> "4.5.0",
      "chart.js" -> "2.9.3",
      "jquery" -> "3.5.1",
      "mapbox-gl" -> mapboxVersion,
      "popper.js" -> "1.16.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.8.0",
      "cssnano" -> "4.1.10",
      "css-loader" -> "3.5.3",
      "file-loader" -> "6.0.0",
      "less" -> "3.11.1",
      "less-loader" -> "6.1.0",
      "mini-css-extract-plugin" -> "0.9.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "1.2.1",
      "url-loader" -> "4.1.0",
      "webpack-merge" -> "4.2.2"
    ),
    version in webpack := "4.43.0",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    webpackConfigFile in fastOptJS := Some(
      baseDirectory.value / "webpack.dev.config.js"
    ),
    webpackConfigFile in fullOptJS := Some(
      baseDirectory.value / "webpack.prod.config.js"
    )
  )

val backend = Project("boat", file("backend"))
  .enablePlugins(PlayScala, FileTreePlugin, WebScalaJSBundlerPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJvm)
  .settings(jvmSettings ++ boatSettings)
  .settings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "docs",
    libraryDependencies ++= Seq(
      "com.vividsolutions" % "jts" % "1.13",
      "io.getquill" %% "quill-jdbc" % "3.5.1",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "org.flywaydb" % "flyway-core" % "6.4.3",
      "org.apache.commons" % "commons-text" % "1.8",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.584",
      "com.malliina" %% "logstreams-client" % "1.10.1",
      "com.malliina" %% "play-social" % utilPlayVersion,
      "com.malliina" %% "mobile-push" % "1.24.0",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.4",
      "org.eclipse.jetty" % "jetty-alpn-java-server" % "9.4.20.v20190813",
      "org.eclipse.jetty" % "jetty-alpn-java-client" % "9.4.20.v20190813",
      utilPlayDep,
      utilPlayTestDep,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test
    ),
    routesImport ++= Seq(
      "com.malliina.boat.Bindables._",
      "com.malliina.boat.TrackName",
      "com.malliina.boat.BoatName"
    ),
    scalaJSProjects := Seq(frontend),
    pipelineStages := Seq(digest, gzip),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    //  npmAssets ++= NpmAssets.ofProject(client) { modules => (modules / "font-awesome").allPaths }.value,
//    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "gitHash" -> gitHash, "mapboxVersion" -> mapboxVersion),
//    buildInfoPackage := "com.malliina.boat",
    // linux packaging
    httpPort in Linux := Option(s"$prodPort"),
    httpsPort in Linux := Option("disabled"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    // WTF?
    linuxPackageSymlinks := linuxPackageSymlinks.value
      .filterNot(_.link == "/usr/bin/starter"),
    javaOptions in Universal ++= {
      Seq(
        "-J-Xmx1024m",
        s"-Dpidfile.path=/dev/null",
        "-Dlogger.resource=logback-prod.xml"
      )
    },
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(clean in Compile),
      checkSnapshotDependencies,
      //      releaseStepInputTask(testOnly, " * -- -l tests.DbTest"),
      //      releaseStepInputTask(testOnly, " tests.ImageTests"),
      releaseStepTask(ciBuild)
    ),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    daemonUser in Docker := "boat",
    version in Docker := gitHash,
    dockerRepository := Option("malliinaboat.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort)
  )

val agent = project
  .in(file("agent"))
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)
  .dependsOn(crossJvm)
  .settings(jvmSettings)
  .settings(
    name in Linux := "boat-agent",
    normalizedName := "boat-agent",
    maintainer := "Michael Skogberg",
    javaOptions in Universal ++= {
      val linuxName = (normalizedName in Debian).value
      Seq(
        s"-Dconf.dir=/usr/share/$linuxName/conf",
        s"-Dlogback.configurationFile=logback-prod.xml",
        s"-Dlog.dir=/var/log/$linuxName"
      )
    },
    linuxPackageMappings += {
      val linuxName = (normalizedName in Debian).value
      packageTemplateMapping(s"/usr/share/$linuxName/conf")()
        .withUser(daemonUser.value)
        .withGroup(daemonUser.value)
    },
    libraryDependencies ++= Seq(
      "com.malliina" %% "primitives" % primitiveVersion,
      "com.malliina" %% "logback-streams" % "1.8.0",
      "com.neovisionaries" % "nv-websocket-client" % "2.9",
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.lihaoyi" %% "scalatags" % scalaTagsVersion,
      "commons-codec" % "commons-codec" % "1.14"
//      "com.neuronrobotics" % "nrjavaserial" % "3.14.0"
    ),
    releaseUseGlobalVersion := false,
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

val it = Project("integration-tests", file("boat-test"))
  .dependsOn(backend, backend % "test->test", agent)
  .disablePlugins(RevolverPlugin)
  .settings(jvmSettings ++ boatSettings)
  .settings(libraryDependencies ++= Seq(utilPlayTestDep))

val utils = project
  .in(file("utils"))
  .dependsOn(crossJvm)
  .disablePlugins(RevolverPlugin)
  .settings(basicSettings ++ boatSettings)
  .settings(
    resolvers ++= Seq(
      "OSGeo Release Repository" at "https://repo.osgeo.org/repository/release/"
    ),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "javax.media" % "jai_core" % "1.1.3",
      "org.geotools" % "gt-shapefile" % "23.0" exclude ("javax.media", "jai_core"),
      "org.geotools" % "gt-geojson" % "23.0" exclude ("javax.media", "jai_core"),
      munitDep
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

val boatRoot = project
  .in(file("."))
  .aggregate(backend, frontend, agent, it, utils)
  .settings(commonSettings ++ boatSettings)

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse --short HEAD").lineStream.head).toOption)
    .getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
