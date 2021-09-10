package com.malliina

import com.malliina.boat.FrontKeys.Hidden
import org.scalajs.dom.raw.{Element, Event, EventTarget, HTMLElement}
import org.scalajs.dom.{DOMList, Node}

package object boat:

  implicit class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T]:
    override def foreach[U](f: T => U): Unit =
      for i <- 0 until nodes.length do f(nodes(i))

    override def length: Int = nodes.length
    override def apply(idx: Int): T = nodes(idx)

  implicit class ElementOps(val e: Element) extends AnyVal:
    def hide(): Unit =
      val classes = e.classList
      if !classes.contains(Hidden) then classes.add(Hidden)

    def show(): Unit = e.classList.remove(Hidden)

    def toggle(cls: String = Hidden): Unit =
      val classes = e.classList
      if !classes.contains(cls) then classes.add(cls)
      else classes.remove(cls)

  implicit class EventTargetOps(val et: EventTarget):
    def addOnClick(code: Event => Unit): Unit = addClickListener[Event](code)
    def addClickListener[E <: Event](code: E => Unit): Unit = et.addEventListener("click", code)
    def isOutside(elem: HTMLElement): Boolean = !isInside(elem)
    def isInside(elem: HTMLElement): Boolean = et match
      case element: HTMLElement => DOM.isInside(element, elem)
      case _                    => false
