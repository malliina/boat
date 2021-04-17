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
    Compile / resources ++= (Compile / resources).value ++ Def.taskDyn {
      val sjsStage = (clientProject / scalaJSStage).value match {
        case Stage.FastOpt => fastOptJS
        case Stage.FullOpt => fullOptJS
      }
      val client = clientProject.value
      Def.task {
        val webpackFiles =
          (client / Compile / sjsStage / webpack).value.map(_.data)
        val hashedFiles =
          (client / Compile / sjsStage / hashAssets).value.map(_.hashedFile.toFile)
        webpackFiles ++ hashedFiles
      }
    }.value,
    Compile / resourceDirectories += Def
      .settingDyn(clientDyn.value / Compile / assetsDir)
      .value
      .toFile,
    reStart := Def
      .inputTaskDyn[AppProcess] {
        reStart
          .toTask(" ")
          .dependsOn(clientDyn.value / Compile / fastOptJS / webpack)
      }
      .evaluated,
    watchSources ++= (clientProject / watchSources).value,
    Compile / sourceGenerators := (Compile / sourceGenerators).value :+ Def
      .taskDyn[Seq[File]] {
        val sjsStage = Def.settingDyn(clientDyn.value / scalaJSStage).value
        val sjsTask = sjsStage match {
          case Stage.FastOpt => fastOptJS
          case Stage.FullOpt => fullOptJS
        }
        val client = clientProject.value
        Def.task[Seq[File]] {
          val dest = (Compile / sourceManaged).value
          val hashed = (client / Compile / sjsTask / hashAssets).value
          val prefix = (client / assetsPrefix).value
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
