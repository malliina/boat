import com.malliina.http.FullUrl
import com.malliina.bundler.HashedFile
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.packager.docker.DockerVersion
import java.nio.file.{Files, Path, StandardCopyOption}

import scala.sys.process.Process
import scala.util.Try

val mapboxVersion = "2.2.0"
val webAuthVersion = "6.0.2"
val munitVersion = "0.7.28"
val testContainersScalaVersion = "0.39.6"
val scalaTagsVersion = "0.9.4"
val primitiveVersion = "2.0.2"
val akkaVersion = "2.6.5"
val akkaHttpVersion = "10.1.12"
val playJsonVersion = "2.9.2"
val logstreamsVersion = "1.11.13-SNAPSHOT"
// Do not upgrade to 11.0.2 because it depends on slf4j-api alpha versions, breaking logging
val alpnVersion = "9.4.40.v20210413"
val webAuthDep = "com.malliina" %% "web-auth" % webAuthVersion
val utilHtmlDep = "com.malliina" %% "util-html" % webAuthVersion
val webAuthTestDep = webAuthDep % Test classifier "tests"
val munitDep = "org.scalameta" %% "munit" % munitVersion % Test
val circeModules = Seq("generic", "parser")

val buildAndUpload = taskKey[FullUrl]("Uploads to S3")
val upFiles = taskKey[Seq[String]]("lists")
val deployDocs = taskKey[Unit]("Deploys documentation")
val prodPort = 9000

ThisBuild / parallelExecution := false
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

val scala213 = "2.13.6"
val scala3 = "3.0.1"

inThisBuild(
  Seq(
    organization := "com.malliina",
    scalaVersion := scala213,
    scalacOptions := Seq("-unchecked", "-deprecation"),
    deployDocs := Process("mkdocs gh-deploy").run(streams.value.log).exitValue(),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty
  )
)

val boatSettings = Seq(
  version := "1.2.0"
)

val jvmSettings = Seq(
  libraryDependencies ++= Seq(
    munitDep
  ),
  testFrameworks += new TestFramework("munit.Framework")
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .disablePlugins(RevolverPlugin)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= circeModules.map(m => "io.circe" %%% s"circe-$m" % "0.14.1") ++ Seq(
      "com.malliina" %%% "primitives" % primitiveVersion,
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion,
      //("com.lihaoyi" %%% "scalatags" % scalaTagsVersion).cross(CrossVersion.for3Use2_13),
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, ClientPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.1.0",
//      ("org.scala-js" %%% "scalajs-dom" % "1.1.0").cross(CrossVersion.for3Use2_13),
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.15.3",
      "@mapbox/mapbox-gl-geocoder" -> "4.7.0",
      "@turf/turf" -> "6.3.0",
      "bootstrap" -> "4.6.0",
      "chart.js" -> "3.0.2",
      "jquery" -> "3.6.0",
      "mapbox-gl" -> mapboxVersion,
      "popper.js" -> "1.16.1"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.2.5",
      "cssnano" -> "4.1.11",
      "css-loader" -> "5.2.1",
      "file-loader" -> "6.2.0",
      "less" -> "4.1.1",
      "less-loader" -> "7.3.0",
      "mini-css-extract-plugin" -> "1.4.1",
      "postcss" -> "8.2.9",
      "postcss-import" -> "14.0.1",
      "postcss-loader" -> "4.2.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "2.0.0",
      "url-loader" -> "4.1.1",
      "webpack-merge" -> "5.7.3"
    ),
    webpack / version := "4.44.2",
    webpackEmitSourceMaps := true,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    fastOptJS / webpackConfigFile := Some(
      baseDirectory.value / "webpack.dev.config.js"
    ),
    fullOptJS / webpackConfigFile := Some(
      baseDirectory.value / "webpack.prod.config.js"
    ),
    Compile / fullOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) }
  )

val http4sModules = Seq("blaze-server", "blaze-client", "dsl", "scalatags", "circe")

val backend = Project("boat", file("backend"))
  .enablePlugins(
    RevolverPlugin,
    ServerPlugin,
    FileTreePlugin,
    JavaServerAppPackaging,
    SystemdPlugin,
    BuildInfoPlugin
  )
  .dependsOn(crossJvm)
  .settings(jvmSettings ++ boatSettings)
  .settings(
    Compile / unmanagedResourceDirectories ++= Seq(
      baseDirectory.value / "docs"
    ),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.22.2"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.13.4"
    } ++ Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % "1.2.5"
    } ++ Seq("server", "client").map { m =>
      "org.eclipse.jetty" % s"jetty-alpn-java-$m" % alpnVersion
    } ++ Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.16.0",
      //("com.github.pureconfig" %% "pureconfig" % "0.16.0").cross(CrossVersion.for3Use2_13),
      "com.vividsolutions" % "jts" % "1.13",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "org.flywaydb" % "flyway-core" % "7.14.0",
      "org.apache.commons" % "commons-text" % "1.9",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.856",
      "com.malliina" %% "logstreams-client" % logstreamsVersion,
      "com.malliina" %% "mobile-push-io" % "3.0.1",
      "org.slf4j" % "slf4j-api" % "1.7.32",
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5",
      utilHtmlDep,
      webAuthDep,
      webAuthTestDep,
      munitDep,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test
    ),
    clientProject := frontend,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      "gitHash" -> gitHash,
      "mapboxVersion" -> mapboxVersion,
      "mode" -> (if ((Global / scalaJSStage).value == FullOptStage) "prod" else "dev")
    ),
    buildInfoPackage := "com.malliina.boat",
    // linux packaging
    Linux / httpPort := Option(s"$prodPort"),
    Linux / httpsPort := Option("disabled"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    Universal / javaOptions ++= {
      Seq(
        "-J-Xmx1024m",
        s"-Dpidfile.path=/dev/null",
        "-Dlogback.configurationFile=logback-prod.xml"
      )
    },
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(Compile / clean),
      checkSnapshotDependencies,
      releaseStepTask(ciBuild)
    ),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    Docker / daemonUser := "boat",
    Docker / version := gitHash,
    dockerRepository := Option("malliinacr.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty
  )

val agent = project
  .in(file("agent"))
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)
  .dependsOn(crossJvm)
  .settings(jvmSettings)
  .settings(
    Linux / name := "boat-agent",
    normalizedName := "boat-agent",
    maintainer := "Michael Skogberg",
    Universal / javaOptions ++= {
      val linuxName = (Debian / normalizedName).value
      Seq(
        s"-Dconf.dir=/usr/share/$linuxName/conf",
        s"-Dlogback.configurationFile=logback-prod.xml",
        s"-Dlog.dir=/var/log/$linuxName"
      )
    },
    linuxPackageMappings += {
      val linuxName = (Debian / normalizedName).value
      packageTemplateMapping(s"/usr/share/$linuxName/conf")()
        .withUser(daemonUser.value)
        .withGroup(daemonUser.value)
    },
    libraryDependencies ++=
      Seq("blaze-server", "blaze-client", "dsl", "circe").map { m =>
        "org.http4s" %% s"http4s-$m" % "0.22.2"
      } ++ Seq("generic", "parser").map { m =>
        "io.circe" %% s"circe-$m" % "0.14.1"
      } ++ Seq(
        "co.fs2" %% "fs2-io" % "2.5.9",
        "com.malliina" %% "primitives" % primitiveVersion,
//      "com.malliina" %% "logback-fs2" % logstreamsVersion,
        "com.malliina" %% "logstreams-client" % logstreamsVersion, // temporary until websocket client is available in okclient
        "com.neovisionaries" % "nv-websocket-client" % "2.14",
        "org.slf4j" % "slf4j-api" % "1.7.32",
        "com.lihaoyi" %% "scalatags" % scalaTagsVersion,
        //      ("com.lihaoyi" %% "scalatags" % scalaTagsVersion).cross(CrossVersion.for3Use2_13),
        "commons-codec" % "commons-codec" % "1.15"
      ),
    releaseUseGlobalVersion := false,
    buildAndUpload := {
      val debFile = (Debian / packageBin).value
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
  .settings(
    libraryDependencies ++= Seq(webAuthTestDep)
  )

val utils = project
  .in(file("utils"))
  .dependsOn(crossJvm)
  .disablePlugins(RevolverPlugin)
  .settings(boatSettings)
  .settings(
    resolvers ++= Seq(
      "OSGeo Release Repository" at "https://repo.osgeo.org/repository/release/"
    ),
    libraryDependencies ++= Seq("shapefile", "geojson").map { m =>
      "org.geotools" % s"gt-$m" % "23.0" exclude ("javax.media", "jai_core")
    } ++ Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      "org.slf4j" % "slf4j-api" % "1.7.32",
      "javax.media" % "jai_core" % "1.1.3",
      munitDep
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

val boatRoot = project
  .in(file("."))
  .aggregate(backend, frontend, agent, it, utils)
  .settings(boatSettings)

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
