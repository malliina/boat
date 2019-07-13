import sbt.Keys._
import sbt._
import complete.DefaultParsers._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport.npmUpdate

import scala.sys.process.{Process, ProcessLogger}

object NodeCheckPlugin extends AutoPlugin {
  object autoImport {
    val checkNode = taskKey[Unit]("Make sure the user uses the correct version of node.js")
    val failMode =
      settingKey[FailMode]("Whether to warn or fail hard when the node version is unsupported")
    val ncu = taskKey[Int]("Runs npm-check-updates")
    val front = inputKey[Int]("Runs the input as a command in the frontend working directory")
  }
  import autoImport._

  override val globalSettings: Seq[Def.Setting[_]] = Seq(
    failMode := FailMode.Warn,
    checkNode := runNodeCheck(streams.value.log, failMode.value),
    onLoad in Global := (onLoad in Global).value andThen { state =>
      "checkNode" :: state
    }
  )

  override val projectSettings = Seq(
    ncu := front.toTask(" ncu").value,
    front := {
      val log = streams.value.log
      val cwd = (crossTarget in (Compile, npmUpdate)).value
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      log.info(s"Running '${args.mkString(" ")}' in $cwd...")
      val status = Process(args, cwd).run(log).exitValue()
      if (status != 0) {
        log.error(s"Exited with status $status.")
      }
      status
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
