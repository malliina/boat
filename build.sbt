import com.malliina.build.FileIO
import okhttp3.OkHttpClient
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations.*

import scala.sys.process.Process

val webAuthVersion = "6.7.0"
val munitVersion = "0.7.29"
val testContainersScalaVersion = "0.41.3"
val scalaTagsVersion = "0.12.0"
val primitiveVersion = "3.6.0"
val logstreamsVersion = "2.7.0"
val http4sVersion = "0.23.26"
val logbackVersion = "1.5.5"
val circeVersion = "0.14.6"
val alpnVersion = "12.0.8"
val webAuthDep = "com.malliina" %% "web-auth" % webAuthVersion
val webAuthTestDep = webAuthDep % Test classifier "tests"
val munitDep = "org.scalameta" %% "munit" % munitVersion % Test

val buildAndUpload = taskKey[String]("Uploads to S3, returns a URL")
val upFiles = taskKey[Seq[String]]("lists")
val deployDocs = taskKey[Unit]("Deploys documentation")

ThisBuild / parallelExecution := false
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

val scala213 = "2.13.6"
val scala3 = "3.4.0"

inThisBuild(
  Seq(
    organization := "com.malliina",
    scalaVersion := scala3,
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
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    scalacOptions ++= Seq(
      "-Wunused:all"
    )
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

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .disablePlugins(RevolverPlugin)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % circeVersion
    } ++ Seq(
      "com.comcast" %% "ip4s-core" % "3.5.0",
      "org.typelevel" %%% "case-insensitive" % "1.4.0",
      "com.malliina" %%% "primitives" % primitiveVersion,
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    )
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val runNpmInstall = taskKey[Unit]("Updates the package-lock.json file")
val updatePackageLockJson = taskKey[Unit]("Updates the package-lock.json file")

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, RollupPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(boatSettings)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % "3.9.4",
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    runNpmInstall := RollupPlugin.npmInstall(npmRoot.value, streams.value.log),
    updatePackageLockJson := FileIO.copyIfChanged(
      (target.value / "package-lock.json").toPath,
      ((Compile / resourceDirectory).value / "package-lock.json").toPath
    ),
    updatePackageLockJson := updatePackageLockJson.dependsOn(runNpmInstall).value
  )

val backend = Project("boat", file("backend"))
  .enablePlugins(ServerPlugin, DebPlugin)
  .dependsOn(crossJvm)
  .settings(jvmSettings ++ boatSettings)
  .settings(
    Compile / unmanagedResourceDirectories ++= Seq(
      baseDirectory.value / "docs"
    ),
    libraryDependencies ++= Seq("ember-server", "ember-client", "dsl", "circe").map { m =>
      "org.http4s" %% s"http4s-$m" % http4sVersion
    } ++ Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % logbackVersion
    } ++ Seq("server", "client").map { m =>
      "org.eclipse.jetty" % s"jetty-alpn-java-$m" % alpnVersion
    } ++ Seq("util-html", "database").map { m =>
      "com.malliina" %% m % webAuthVersion
    } ++ Seq(
      "com.vividsolutions" % "jts" % "1.13",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "org.apache.commons" % "commons-text" % "1.11.0",
      "software.amazon.awssdk" % "s3" % "2.25.31",
      "com.malliina" %% "logstreams-client" % logstreamsVersion,
      "com.malliina" %% "mobile-push-io" % "3.10.0",
      "com.malliina" %% "config" % primitiveVersion,
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5",
      webAuthDep,
      webAuthTestDep,
      munitDep,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
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
      Seq("ember-server", "dsl", "circe").map { m =>
        "org.http4s" %% s"http4s-$m" % http4sVersion
      } ++ Seq("generic", "parser").map { m =>
        "io.circe" %% s"circe-$m" % circeVersion
      } ++ Seq(
        "co.fs2" %% "fs2-io" % "3.9.4",
        "com.malliina" %% "primitives" % primitiveVersion,
        "com.malliina" %% "logstreams-client" % logstreamsVersion,
        "com.lihaoyi" %% "scalatags" % scalaTagsVersion,
        "commons-codec" % "commons-codec" % "1.17.0",
        "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
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
      "org.geotools" % s"gt-$m" % "30.2" exclude ("javax.media", "jai_core")
    } ++ Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
//      "javax.media" % "jai_core" % "1.1.3",
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
      "LATEST_AGENT_URL" -> LatestClient.default.latest
        .map(_.url)
        .getOrElse("todo"),
      "LATEST_AGENT_FILE" -> LatestClient.default.latest
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
  .aggregate(backend, frontend, agent, it, utils)
  .settings(boatSettings)

Global / onChangedBuildSource := ReloadOnSourceChanges
