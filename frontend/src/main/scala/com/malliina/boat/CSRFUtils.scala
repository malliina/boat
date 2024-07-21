package com.malliina.boat

import com.malliina.http.CSRFConf
import org.scalajs.dom
import org.scalajs.dom.HTMLFormElement
import org.scalajs.dom.{Element, Event}
import scalatags.JsDom.all.*

class CSRFUtils(conf: CSRFConf, val log: BaseLogger = BaseLogger.console):
  val document = dom.document

  def installCsrf(parent: Element): Unit =
    parent
      .getElementsByTagName("form")
      .foreach: node =>
        node.addEventListener(
          "submit",
          (e: Event) => installTo(e.target.asInstanceOf[HTMLFormElement])
        )

  private def installTo(form: HTMLFormElement) =
    readCookie(conf.cookieName)
      .map: tokenValue =>
        form.appendChild(csrfInput(conf.tokenName, tokenValue).render)
      .getOrElse:
        log.info("CSRF token not found.")

  private def readCookie(key: String) =
    cookiesMap(document.cookie).get(key)

  private def cookiesMap(in: String) =
    in.split(";")
      .toList
      .map(_.trim.split("=", 2).toList)
      .collect:
        case key :: value :: Nil =>
          key -> value
      .toMap

  private def csrfInput(inputName: String, inputValue: String) =
    input(`type` := "hidden", name := inputName, value := inputValue)
