import com.malliina.http.FullUrl
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._

import scala.sys.process.Process
import scala.util.Try

val mapboxVersion = "0.53.1"
val utilPlayVersion = "5.1.1"
val scalaTestVersion = "3.0.7"
val scalaTagsVersion = "0.6.7"
val primitiveVersion = "1.9.0"
val akkaVersion = "2.5.20"
val akkaHttpVersion = "10.1.7"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion
val utilPlayTestDep = utilPlayDep % Test classifier "tests"
val scalaTestDep = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
val nvWebsocketDep = "com.neovisionaries" % "nv-websocket-client" % "2.8"
val buildAndUpload = taskKey[FullUrl]("Uploads to S3")
val upFiles = taskKey[Seq[String]]("lists")
val bootClasspath = taskKey[String]("bootClasspath")
val deployDocs = taskKey[Unit]("Deploys documentation")

parallelExecution in ThisBuild := false

val basicSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.12.8",
  scalacOptions := Seq("-unchecked", "-deprecation")
)

val commonSettings = basicSettings ++ Seq(
  deployDocs := Process("mkdocs gh-deploy").run(streams.value.log).exitValue(),
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("malliina", "maven"),
    Resolver.mavenLocal
  )
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.malliina" %%% "primitives" % primitiveVersion,
      "com.lihaoyi" %%% "scalatags" % scalaTagsVersion,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    )
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb, NodeCheckPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % "2.7.1",
      "org.scala-js" %%% "scalajs-dom" % "0.9.6",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    ),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.8.1",
      "@mapbox/mapbox-gl-geocoder" -> "3.1.6",
      "@turf/turf" -> "5.1.6",
      "bootstrap" -> "4.2.1",
      "chart.js" -> "2.7.3",
      "jquery" -> "3.3.1",
      "mapbox-gl" -> mapboxVersion,
      "popper.js" -> "1.14.6"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.4.3",
      "cssnano" -> "4.1.8",
      "css-loader" -> "2.1.0",
      "file-loader" -> "3.0.1",
      "less" -> "3.9.0",
      "less-loader" -> "4.1.0",
      "mini-css-extract-plugin" -> "0.5.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.5.0",
      "style-loader" -> "0.23.1",
      "url-loader" -> "1.1.2",
      "webpack-merge" -> "4.1.5"
    ),
    version in webpack := "4.27.1",
    emitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val backend = Project("boat", file("backend"))
  .enablePlugins(FileTreePlugin, WebScalaJSBundlerPlugin, PlayLinuxPlugin)
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    version := "1.0.0",
    unmanagedResourceDirectories in Compile += baseDirectory.value / "docs",
    libraryDependencies ++= Seq(
      //    "net.sf.marineapi" % "marineapi" % "0.13.0-SNAPSHOT",
      "com.vividsolutions" % "jts" % "1.13",
      "com.typesafe.slick" %% "slick" % "3.2.3",
      "com.h2database" % "h2" % "1.4.196",
      "org.orbisgis" % "h2gis" % "1.4.0",
      "mysql" % "mysql-connector-java" % "5.1.47",
      "com.zaxxer" % "HikariCP" % "3.3.1",
      "org.flywaydb" % "flyway-core" % "5.2.4",
      "org.apache.commons" % "commons-text" % "1.6",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.466",
      "com.malliina" %% "logstreams-client" % "1.5.0",
      "com.malliina" %% "play-social" % utilPlayVersion,
      "com.malliina" %% "mobile-push" % "1.18.3",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.0",
      utilPlayDep,
      utilPlayTestDep
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
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "gitHash" -> gitHash, "mapboxVersion" -> mapboxVersion),
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

val agent = project
  .in(file("agent"))
  .enablePlugins(JavaServerAppPackaging, DebianPlugin, SystemdPlugin)
  .dependsOn(crossJvm)
  .settings(commonSettings)
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
      packageTemplateMapping(s"/usr/share/$linuxName/conf")().withUser(daemonUser.value).withGroup(daemonUser.value)
    },
    libraryDependencies ++= Seq(
      "com.malliina" %% "primitives" % primitiveVersion,
      "com.malliina" %% "logback-streams" % "1.5.0",
      nvWebsocketDep,
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.lihaoyi" %% "scalatags" % scalaTagsVersion,
      "commons-codec" % "commons-codec" % "1.12",
      "com.neuronrobotics" % "nrjavaserial" % "3.14.0",
      scalaTestDep
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
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      utilPlayTestDep
    )
  )

val utils = project
  .in(file("utils"))
  .dependsOn(crossJvm)
  .settings(basicSettings)
  .settings(
    resolvers += "GeoTools" at "https://download.osgeo.org/webdav/geotools/",
    libraryDependencies ++= Seq(
      "javax.media" % "jai_core" % "1.1.3" from "https://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
      "org.geotools" % "gt-shapefile" % "20.0" exclude ("javax.media", "jai_core"),
      "org.geotools" % "gt-geojson" % "20.0" exclude ("javax.media", "jai_core"),
      scalaTestDep
    )
  )

val boatRoot = project
  .in(file("."))
  .aggregate(backend, frontend, agent, it, utils)
  .settings(commonSettings)

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")
