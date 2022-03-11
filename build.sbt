import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.packager.docker.DockerVersion

import scala.sys.process.Process
import scala.util.Try

val mapboxVersion = "2.5.1"
val webAuthVersion = "6.2.2"
val munitVersion = "0.7.29"
val testContainersScalaVersion = "0.40.2"
val scalaTagsVersion = "0.11.1"
val primitiveVersion = "3.1.3"
val logstreamsVersion = "2.1.6"
val http4sVersion = "0.23.10"
val slf4jVersion = "1.7.36"
val logbackVersion = "1.2.11"
// Do not upgrade to 11.0.2 because it depends on slf4j-api alpha versions, breaking logging
val alpnVersion = "9.4.40.v20210413"
val webAuthDep = "com.malliina" %% "web-auth" % webAuthVersion
val utilHtmlDep = "com.malliina" %% "util-html" % webAuthVersion
val webAuthTestDep = webAuthDep % Test classifier "tests"
val munitDep = "org.scalameta" %% "munit" % munitVersion % Test
val circeModules = Seq("generic", "parser")

val buildAndUpload = taskKey[String]("Uploads to S3, returns a URL")
val upFiles = taskKey[Seq[String]]("lists")
val deployDocs = taskKey[Unit]("Deploys documentation")

ThisBuild / parallelExecution := false
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

val scala213 = "2.13.6"
val scala3 = "3.1.1"

inThisBuild(
  Seq(
    organization := "com.malliina",
    scalaVersion := scala3,
    scalacOptions := Seq("-unchecked", "-deprecation"),
    deployDocs := Process("mkdocs gh-deploy").run(streams.value.log).exitValue(),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.rename
      case PathList("com", "malliina", xs @ _*)         => MergeStrategy.first
      case PathList("module-info.class")         => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
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
      "org.scala-js" %%% "scalajs-dom" % "2.1.0",
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.15.4",
      "@mapbox/mapbox-gl-geocoder" -> "4.7.4",
      "@popperjs/core" -> "2.10.2",
      "@turf/turf" -> "6.5.0",
      "bootstrap" -> "5.1.3",
      "chart.js" -> "3.5.1",
      "mapbox-gl" -> mapboxVersion
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.4.1",
      "cssnano" -> "5.0.14",
      "css-loader" -> "6.5.1",
      "less" -> "4.1.2",
      "less-loader" -> "10.2.0",
      "mini-css-extract-plugin" -> "2.4.5",
      "postcss" -> "8.4.5",
      "postcss-import" -> "14.0.2",
      "postcss-loader" -> "6.2.1",
      "postcss-preset-env" -> "7.2.0",
      "style-loader" -> "3.3.1",
      "webpack-merge" -> "5.8.0"
    ),
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    Compile / fullOptJS / scalaJSLinkerConfig ~= { _.withSourceMap(false) }
  )

val config = project
  .in(file("config"))
  .settings(jvmSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.2",
      "com.malliina" %% "primitives" % primitiveVersion
    )
  )

val backend = Project("boat", file("backend"))
  .enablePlugins(
    LiveRevolverPlugin,
    ServerPlugin,
    FileTreePlugin,
    JavaServerAppPackaging,
    SystemdPlugin,
    BuildInfoPlugin
  )
  .dependsOn(crossJvm, config)
  .settings(jvmSettings ++ boatSettings)
  .settings(
    Compile / unmanagedResourceDirectories ++= Seq(
      baseDirectory.value / "docs"
    ),
    libraryDependencies ++= Seq("ember-server", "blaze-client", "dsl", "circe").map { m =>
      "org.http4s" %% s"http4s-$m" % http4sVersion
    } ++ Seq("core", "hikari").map { d =>
      "org.tpolecat" %% s"doobie-$d" % "1.0.0-RC2"
    } ++ Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % logbackVersion
    } ++ Seq("server", "client").map { m =>
      "org.eclipse.jetty" % s"jetty-alpn-java-$m" % alpnVersion
    } ++ Seq(
      "com.vividsolutions" % "jts" % "1.13",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "org.flywaydb" % "flyway-core" % "7.15.0",
      "org.apache.commons" % "commons-text" % "1.9",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.12.150",
      "com.malliina" %% "logstreams-client" % logstreamsVersion,
      "com.malliina" %% "mobile-push-io" % "3.4.2",
      "org.slf4j" % "slf4j-api" % "1.7.36",
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
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    Universal / javaOptions ++= Seq(
      "-J-Xmx1024m",
      s"-Dpidfile.path=/dev/null",
      "-Dlogback.configurationFile=logback-prod.xml"
    ),
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(Compile / clean),
      checkSnapshotDependencies
      //releaseStepTask(ciBuild)
    ),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "public",
    Compile / unmanagedResourceDirectories += (frontend / Compile / assetsRoot).value.getParent.toFile,
    assembly / assemblyJarName := "app.jar"
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
        "org.http4s" %% s"http4s-$m" % http4sVersion
      } ++ Seq("generic", "parser").map { m =>
        "io.circe" %% s"circe-$m" % "0.14.1"
      } ++ Seq(
        "co.fs2" %% "fs2-io" % "3.1.3",
        "com.malliina" %% "primitives" % primitiveVersion,
        "com.malliina" %% "logstreams-client" % logstreamsVersion,
        "org.slf4j" % "slf4j-api" % slf4jVersion,
        "com.lihaoyi" %% "scalatags" % scalaTagsVersion,
        "commons-codec" % "commons-codec" % "1.15"
      ),
    releaseUseGlobalVersion := false,
    buildAndUpload := {
      val debFile = (Debian / packageBin).value
      val filename = S3Client.upload(debFile.toPath)
      val url = s"https://www.boat-tracker.com/files/$filename"
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
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
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
