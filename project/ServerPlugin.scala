import ClientPlugin.autoImport.{assetsDir, assetsPrefix, hashAssets}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{fastOptJS, fullOptJS, scalaJSStage}
import org.scalajs.sbtplugin.Stage
import sbt.Keys._
import sbt._
import sbt.internal.util.ManagedLogger
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.webpack
import spray.revolver.AppProcess
import spray.revolver.RevolverPlugin.autoImport.reStart

import java.nio.charset.StandardCharsets

object ServerPlugin extends AutoPlugin {
  object autoImport {
    val assetsPackage = settingKey[String]("Package name of generated assets file")
    val clientProject = settingKey[Project]("Scala.js project")
  }
  import autoImport._

  val clientDyn = Def.settingDyn(clientProject)

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    assetsPackage := "com.malliina.assets",
    resources in Compile ++= resources.in(Compile).value ++ Def.taskDyn {
      val sjsStage = scalaJSStage.in(clientProject).value match {
        case Stage.FastOpt => fastOptJS
        case Stage.FullOpt => fullOptJS
      }
      val client = clientProject.value
      Def.task {
        val webpackFiles =
          webpack.in(client, Compile, sjsStage).value.map(_.data)
        val hashedFiles =
          hashAssets.in(client, Compile, sjsStage).value.map(_.hashedFile.toFile)
        webpackFiles ++ hashedFiles
      }
    }.value,
    resourceDirectories in Compile += Def
      .settingDyn(assetsDir.in(clientDyn.value, Compile))
      .value
      .toFile,
    reStart := Def
      .inputTaskDyn[AppProcess] {
        reStart
          .toTask(" ")
          .dependsOn(webpack.in(clientDyn.value, Compile, fastOptJS))
      }
      .evaluated,
    watchSources ++= watchSources.in(clientProject).value,
    sourceGenerators in Compile := sourceGenerators.in(Compile).value :+ Def
      .taskDyn[Seq[File]] {
        val sjsStage = Def.settingDyn(scalaJSStage.in(clientDyn.value)).value
        val sjsTask = sjsStage match {
          case Stage.FastOpt => fastOptJS
          case Stage.FullOpt => fullOptJS
        }
        val client = clientProject.value
        Def.task[Seq[File]] {
          val dest = (sourceManaged in Compile).value
          val hashed = hashAssets.in(client, Compile, sjsTask).value
          val prefix = assetsPrefix.in(client).value
          val log = streams.value.log
          val cached = FileFunction.cached(streams.value.cacheDirectory / "assets") { in =>
            makeAssetsFile(dest, assetsPackage.value, prefix, hashed, log)
          }
          cached(hashed.map(_.hashedFile.toFile).toSet).toSeq
        }
      }
      .taskValue
  )

  def makeAssetsFile(
    base: File,
    packageName: String,
    prefix: String,
    hashes: Seq[HashedFile],
    log: ManagedLogger
  ): Set[File] = {
    val inlined = hashes.map(h => s""""${h.path}" -> "${h.hashedPath}"""").mkString(", ")
    val objectName = "HashedAssets"
    val content =
      s"""
         |package $packageName
         |
         |object $objectName {
         |  val prefix: String = "$prefix"
         |  val assets: Map[String, String] = Map($inlined)
         |}
         |""".stripMargin.trim + IO.Newline
    val destFile = destDir(base, packageName) / s"$objectName.scala"
    IO.write(destFile, content, StandardCharsets.UTF_8)
    log.info(s"Wrote $destFile.")
    Set(destFile)
  }

  def destDir(base: File, packageName: String): File =
    packageName.split('.').foldLeft(base)((acc, part) => acc / part)
}
