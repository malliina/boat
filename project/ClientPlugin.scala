import org.apache.ivy.util.ChecksumHelper
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.Stage
import sbt.Keys._
import sbt._
import sbt.internal.util.ManagedLogger
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

import java.nio.file.{Files, Path, StandardCopyOption}

case class HashedFile(path: String, hashedPath: String, originalFile: Path, hashedFile: Path)

object ClientPlugin extends AutoPlugin {
  override def requires = ScalaJSBundlerPlugin
  object autoImport {
    val assetsDir = settingKey[Path]("Webpack assets dir to serve in server")
    val assetsPrefix = settingKey[String]("Assets prefix")
    val prepTarget = taskKey[Path]("Prep target dir")
    val hashAssets = taskKey[Seq[HashedFile]]("Hashed files")
  }
  import autoImport._
  override def projectSettings: Seq[Def.Setting[_]] =
    stageSettings(Stage.FastOpt) ++ stageSettings(Stage.FullOpt) ++ Seq(
      assetsDir := (baseDirectory.value / "target" / "assets").toPath,
      assetsPrefix := "public",
      prepTarget := Files.createDirectories(assetsDir.value.resolve(assetsPrefix.value))
    )

  private def stageSettings(stage: Stage): Seq[Def.Setting[_]] = {
    val stageTask = stage match {
      case Stage.FastOpt => fastOptJS
      case Stage.FullOpt => fullOptJS
    }
    Seq(
      (Compile / stageTask / hashAssets) := {
        val files = (Compile / stageTask / webpack).value
        val log = streams.value.log
        files.flatMap { file =>
          val root = assetsDir.value.resolve(assetsPrefix.value)
          val relativeFile = file.data.relativeTo(root.toFile).get
          val dest = file.data.toPath
          val extraFiles =
            if (!relativeFile.toPath.startsWith("static")) {
              val hashed = prepFile(dest, log)
              List(
                HashedFile(
                  root.relativize(dest).toString.replace('\\', '/'),
                  root.relativize(hashed).toString.replace('\\', '/'),
                  dest,
                  hashed
                )
              )
            } else {
              Nil
            }
          extraFiles
        }
      },
      Compile / stageTask / webpack := {
        val files = (Compile / stageTask / webpack).value
        val log = streams.value.log
        files.map { file =>
          val relativeFile = file.data.relativeTo((Compile / npmUpdate / crossTarget).value).get
          val dest = assetsDir.value.resolve(assetsPrefix.value).resolve(relativeFile.toPath)
          val path = file.data.toPath
          Files.createDirectories(dest.getParent)
          Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
          log.debug(s"Wrote '$dest', ${Files.size(path)} bytes.")
          Files.createDirectories(dest.getParent)
          file.copy(dest.toFile)(file.metadata)
        }
      },
      Compile / stageTask / webpack := (Compile / stageTask / webpack).dependsOn(prepTarget).value
    )
  }

  def prepFile(file: Path, log: ManagedLogger) = {
    val algorithm = "md5"
    val checksum = ChecksumHelper.computeAsString(file.toFile, algorithm)
    val checksumFile = file.getParent.resolve(s"${file.getFileName}.$algorithm")
    if (!Files.exists(checksumFile)) {
      Files.writeString(checksumFile, checksum)
      log.info(s"Wrote $checksumFile.")
    }
    val (base, ext) = file.toFile.baseAndExt
    val hashedFile = file.getParent.resolve(s"$base.$checksum.$ext")
    if (!Files.exists(hashedFile)) {
      Files.copy(file, hashedFile)
      log.info(s"Wrote $hashedFile.")
    }
    hashedFile
  }
}
