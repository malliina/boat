package com.malliina.boat

import org.scalajs.dom.HTMLElement

object DOM:
  def isInside(target: HTMLElement, elem: HTMLElement): Boolean =
    if target == elem then true
    else
      Option(target.parentElement).fold(false): parent =>
        isInside(parent, elem)
