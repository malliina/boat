package com.malliina.boat

import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

package object html {
  implicit val uriAttr: AttrValue[Uri] = new AttrValue[Uri] {
    override def apply(t: Builder, a: Attr, v: Uri): Unit =
      t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))
  }
}
