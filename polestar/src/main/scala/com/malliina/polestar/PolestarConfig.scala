package com.malliina.polestar

import com.malliina.config.{ConfigNode, ConfigReadable}
import com.malliina.polestar.Polestar.Creds
import com.malliina.values.{Password, Username}

import java.nio.file.Paths

object PolestarConfig:
  private val userHome = Paths.get(sysPropsUnsafe("user.home"))
  val appDir = userHome.resolve(".boat")

  def conf = local("boat.conf")

  private def local(file: String) =
    ConfigNode
      .default(appDir.resolve(file))
      .parse[Creds]("polestar")
      .fold(err => throw Exception(err.message.message), identity)

  given ConfigReadable[Creds] = ConfigReadable.node.emap: n =>
    for
      user <- n.parse[Username]("username")
      pass <- n.parse[Password]("password")
    yield Creds(user, pass)

  private def sysProps(key: String) =
    sys.props.get(key).toRight(s"System property not found: '$key'.")

  private def sysPropsUnsafe(key: String) =
    sysProps(key).fold(err => throw java.util.NoSuchElementException(err), identity)
