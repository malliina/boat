package com.malliina.boat

import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

package object html:
  given AttrValue[Uri] = (t: Builder, a: Attr, v: Uri) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))
