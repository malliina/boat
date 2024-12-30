package com.malliina.boat

import com.malliina.boat.FrontKeys.Hidden
import org.scalajs.dom.{DOMList, Element, Event, EventTarget, HTMLElement, Node}

given [T <: Node]: Conversion[DOMList[T], NodeListSeq[T]] = domList => NodeListSeq(domList)

class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T]:
  override def foreach[U](f: T => U): Unit =
    for i <- 0 until nodes.length do f(nodes(i))

  override def length: Int = nodes.length

  override def apply(idx: Int): T = nodes(idx)

extension (e: Element)
  def hide(): Unit =
    ensure(Hidden)

  def show(): Unit =
    ensureNot(Hidden)

  def toggle(cls: String = Hidden): Unit =
    val classes = e.classList
    if !classes.contains(cls) then classes.add(cls)
    else classes.remove(cls)

  def ensure(cls: String): Unit =
    val classes = e.classList
    if !classes.contains(cls) then classes.add(cls)

  def ensureNot(cls: String): Unit =
    e.classList.remove(cls)

extension (et: EventTarget)
  def addOnClick(code: Event => Unit): Unit = addClickListener[Event](code)
  private def addClickListener[E <: Event](code: E => Unit): Unit =
    et.addEventListener("click", code)
  def isOutside(elem: HTMLElement): Boolean = !isInside(elem)
  def isInside(elem: HTMLElement): Boolean = et match
    case element: HTMLElement => DOM.isInside(element, elem)
    case _                    => false
