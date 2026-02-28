import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations.*

import scala.sys.process.Process

val versions = new {
  val scala213 = "2.13.16"
  val scala3 = "3.8.1"

  val alpn = "12.0.16"
  val ci = "1.4.2"
  val circe = "0.14.15"
  val codec = "1.21.0"
  val commonsText = "1.15.0"
  val fs2 = "3.11.0"
  val http4s = "0.23.33"
  val ip4s = "3.7.0"
  val jts = "1.13"
  val logback = "1.5.32"
  val mariadb = "3.5.7"
  val mobilePush = "3.16.1"
  val munit = "1.2.3"
  val munitCe = "2.1.0"
  val paho = "1.2.5"
  val s3 = "2.42.4"
  val scalaJsDom = "2.8.1"
  val scalaTags = "0.13.1"
  val util = "6.11.1"
}

val webAuthDep = "com.malliina" %% "web-auth" % versions.util
val webAuthTestDep = webAuthDep % Test classifier "tests"
val munitDep = "org.scalameta" %% "munit" % versions.munit % Test

val buildAndUpload = taskKey[String]("Uploads to S3, returns a URL")
val upFiles = taskKey[Seq[String]]("lists")
val deployDocs = taskKey[Unit]("Deploys documentation")

ThisBuild / parallelExecution := false
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

inThisBuild(
  Seq(
    organization := "com.malliina",
    scalaVersion := versions.scala3,
    scalacOptions := Seq("-unchecked", "-deprecation"),
    deployDocs := Process("mkdocs gh-deploy").run(streams.value.log).exitValue(),
    Compile / packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*)            => MergeStrategy.first
      case PathList("META-INF", "okio.kotlin_module")           => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)                 => MergeStrategy.first
      case PathList("module-info.class")                        => MergeStrategy.first
      case x                                                    =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    scalacOptions ++= Seq(
      "-Wunused:all"
    ),
    // https://users.scala-lang.org/t/scala-js-with-3-7-0-package-scala-contains-object-and-package-with-same-name-caps/10786/6
    dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value
  )
)

val boatSettings = Seq(
  version := "1.2.0"
)

val jvmSettings = Seq(
  libraryDependencies ++= Seq(
    munitDep
  )
)

val mapbox = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("mapbox"))
  .enablePlugins(MavenCentralPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    Test / fork := true,
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % versions.circe
    } ++ Seq(
      "com.malliina" %%% "primitives" % versions.util,
      "com.lihaoyi" %%% "scalatags" % versions.scalaTags,
      "org.scalameta" %%% "munit" % versions.munit % Test
    )
  )

val mapboxJvm = mapbox.jvm
val mapboxJs = mapbox.js.settings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % versions.scalaJsDom
  )
)

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .disablePlugins(RevolverPlugin)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % versions.circe
    } ++ Seq(
      "co.fs2" %%% "fs2-core" % versions.fs2,
      "com.comcast" %%% "ip4s-core" % versions.ip4s,
      "org.typelevel" %%% "case-insensitive" % versions.ci,
      "com.malliina" %%% "primitives" % versions.util,
      "com.lihaoyi" %%% "scalatags" % versions.scalaTags,
      "org.scalameta" %%% "munit" % versions.munit % Test
    )
  )

val crossJvm = cross.jvm.dependsOn(mapboxJvm)
val crossJs = cross.js.dependsOn(mapboxJs)

val polestar = project
  .in(file("polestar"))
  .dependsOn(crossJvm)
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % versions.circe
    } ++ Seq("config", "logstreams-client", "okclient-io").map { m =>
      "com.malliina" %% m % versions.util
    } ++ Seq(
      "co.fs2" %% "fs2-io" % versions.fs2,
      "ch.qos.logback" % "logback-classic" % versions.logback,
      "commons-codec" % "commons-codec" % versions.codec,
      "org.scalameta" %% "munit" % versions.munit % Test,
      "org.typelevel" %% "munit-cats-effect" % versions.munitCe % Test
    )
  )

val runNpmInstall = taskKey[Unit]("Updates the package-lock.json file")
val updatePackageLockJson = taskKey[Unit]("Updates the package-lock.json file")

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, EsbuildPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs, mapboxJs)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.malliina" %%% "util-html" % versions.util,
      "org.scala-js" %%% "scalajs-dom" % versions.scalaJsDom,
      "org.scalameta" %%% "munit" % versions.munit % Test
    )
  )

val backend = Project("boat", file("backend"))
  .enablePlugins(ServerPlugin, DebPlugin)
  .dependsOn(polestar, mapboxJvm)
  .settings(jvmSettings ++ boatSettings)
  .settings(
    Compile / unmanagedResourceDirectories ++= Seq(
      baseDirectory.value / "docs"
    ),
    libraryDependencies ++=
      Seq("server", "client").map { m =>
        "org.eclipse.jetty" % s"jetty-alpn-java-$m" % versions.alpn
      } ++ Seq(
        "config",
        "logstreams-client",
        "util-html",
        "database",
        "util-http4s",
        "web-auth"
      ).map { m =>
        "com.malliina" %% m % versions.util
      } ++ Seq(
        "ch.qos.logback" % "logback-classic" % versions.logback,
        "com.vividsolutions" % "jts" % versions.jts,
        "org.mariadb.jdbc" % "mariadb-java-client" % versions.mariadb,
        "org.apache.commons" % "commons-text" % versions.commonsText,
        "software.amazon.awssdk" % "s3" % versions.s3,
        "com.malliina" %% "mobile-push-io" % versions.mobilePush,
        "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % versions.paho,
        webAuthTestDep,
        munitDep,
        "org.http4s" %% "http4s-ember-client" % versions.http4s % Test,
        "org.typelevel" %% "munit-cats-effect" % versions.munitCe % Test
      ),
    clientProject := frontend,
    dependentModule := crossJvm,
    hashPackage := "com.malliina.assets",
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      scalaVersion
    ),
    buildInfoPackage := "com.malliina.boat",
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(Compile / clean),
      checkSnapshotDependencies
    ),
    Compile / packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    assembly / assemblyJarName := "app.jar",
    Compile / resourceDirectories += io.Path.userHome / ".boat",
    Linux / name := "boat"
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
      Seq("generic", "parser").map { m =>
        "io.circe" %% s"circe-$m" % versions.circe
      } ++ Seq("logstreams-client", "primitives").map { m =>
        "com.malliina" %% m % versions.util
      } ++ Seq(
        "co.fs2" %% "fs2-io" % versions.fs2,
        "com.malliina" %% "util-http4s" % versions.util,
        "com.lihaoyi" %% "scalatags" % versions.scalaTags,
        "commons-codec" % "commons-codec" % versions.codec,
        "org.typelevel" %% "munit-cats-effect" % versions.munitCe % Test
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
      "org.geotools" % s"gt-$m" % "30.2" exclude ("javax.media", "jai_core")
    } ++ Seq(
      "ch.qos.logback" % "logback-classic" % versions.logback,
      munitDep
    )
  )

val updateDocs = taskKey[Unit]("Updates docs")

val docs = project
  .in(file("mdoc"))
  .enablePlugins(MdocPlugin)
  .settings(
    publish / skip := true,
    mdocVariables := Map(
      "LATEST_AGENT_URL" -> LatestClient
        .default(sLog.value)
        .latest
        .map(_.url)
        .getOrElse("todo"),
      "LATEST_AGENT_FILE" -> LatestClient
        .default(sLog.value)
        .latest
        .flatMap(_.uri.split('/').lastOption)
        .getOrElse("todo")
    ),
    mdocIn := (ThisBuild / baseDirectory).value / "docs-src",
    mdocOut := (ThisBuild / baseDirectory).value / "docs",
    updateDocs := {
      val log = streams.value.log
      val outFile = mdocOut.value
    },
    updateDocs := updateDocs.dependsOn(mdoc.toTask("")).value
  )

val boatRoot = project
  .in(file("."))
  .aggregate(backend, frontend, agent, it, utils, polestar)
  .settings(boatSettings)

Global / onChangedBuildSource := ReloadOnSourceChanges
