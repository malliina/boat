package com.malliina.boat

import org.scalajs.dom.raw.HTMLElement

object DOM {
  def isInside(target: HTMLElement, elem: HTMLElement): Boolean =
    if (target == elem) {
      true
    } else {
      Option(target.parentElement).fold(false) { parent =>
        isInside(parent, elem)
      }
    }
}
