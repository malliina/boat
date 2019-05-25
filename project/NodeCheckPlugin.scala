import NodeCheckPlugin.autoImport.ncu
import sbt.Keys._
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.npmUpdate

import scala.sys.process.{Process, ProcessLogger}

object NodeCheckPlugin extends AutoPlugin {
  object autoImport {
    val checkNode = taskKey[Unit]("Make sure the user uses the correct version of node.js")
    val failMode =
      settingKey[FailMode]("Whether to warn or fail hard when the node version is unsupported")
    val ncu = taskKey[Int]("Runs npm-check-updates")
  }
  import autoImport.{checkNode, failMode}

  override val globalSettings: Seq[Def.Setting[_]] = Seq(
    failMode := FailMode.Warn,
    checkNode := runNodeCheck(streams.value.log, failMode.value),
    onLoad in Global := (onLoad in Global).value andThen { state =>
      "checkNode" :: state
    }
  )

  override val projectSettings = Seq(
    ncu := {
      val log = streams.value.log
      val cwd = (crossTarget in (Compile, npmUpdate)).value
      log.info(s"Running 'ncu' in $cwd...")
      Process("ncu", cwd).run(log).exitValue()
    }
  )

  def runNodeCheck(log: ProcessLogger, failMode: FailMode) = {
    val nodeVersion = Process("node --version")
      .lineStream(log)
      .toList
      .headOption
      .getOrElse(sys.error(s"Unable to resolve node version."))
    val validPrefixes = Seq("v8")
    if (validPrefixes.exists(p => nodeVersion.startsWith(p))) {
      log.out(s"Using node $nodeVersion")
    } else {
      log.out(s"Node $nodeVersion is unlikely to work. Trying to change version using nvm...")
      try {
        Process("nvm use 8").run(log).exitValue()
      } catch {
        case _: Exception if failMode == FailMode.Warn =>
          log.err(s"Unable to change node version using nvm.")
      }
    }
  }
}

sealed trait FailMode

object FailMode {
  object Warn extends FailMode
  object Fail extends FailMode
}
