package com.malliina.boat

trait BaseLogger {
  def debug(message: String): Unit

  def info(message: String): Unit

  def error(t: Throwable): Unit
}

object BaseLogger {
  val noop: BaseLogger = new BaseLogger {
    override def debug(message: String): Unit = ()

    override def error(t: Throwable): Unit = ()

    override def info(message: String): Unit = ()
  }

  val console: BaseLogger = new BaseLogger {
    override def debug(message: String): Unit = ()

    override def error(t: Throwable): Unit = println(s"Error: ${t.getMessage}")

    override def info(message: String): Unit = println(message)
  }
}
