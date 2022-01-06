package com.malliina.html

import scalatags.Text.all.Frag

case class TagPage(tags: Frag):
  override def toString = tags.toString()
