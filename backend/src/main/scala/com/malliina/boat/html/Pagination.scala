package com.malliina.boat.html

import com.malliina.boat.TrackRef
import com.malliina.boat.http.Limits
import org.http4s.Uri
import scalatags.Text.all.*

class Pagination(lang: PaginationLang) extends BoatSyntax:
  def tracks(track: TrackRef, current: Limits) =
    default(reverse.trackFull(track.trackName), track.points, current)

  def default(base: Uri, totalCount: Int, current: Limits) =
    val pageSize = current.limit
    apply(
      base,
      current.offset + pageSize < totalCount,
      Option(((totalCount - 1) / pageSize) * pageSize),
      current
    )

  def apply(base: Uri, hasMore: Boolean, lastOffset: Option[Int], current: Limits) =
    val pageSize = current.limit
    val prevOffset = math.max(current.offset - pageSize, 0)
    nav(aria.label := "Navigation")(
      ul(cls := "pagination justify-content-center")(
        pageLink(base, Limits(pageSize, 0), lang.first, isDisabled = current.offset == 0),
        pageLink(
          base,
          current.copy(offset = prevOffset),
          lang.previous,
          isDisabled = current.offset == 0
        ),
        pageLink(base, current, "" + current.page, isActive = true),
        pageLink(
          base,
          current.next,
          lang.next,
          isDisabled = !hasMore
        ),
        lastOffset.fold(empty): lastOffset =>
          pageLink(
            base,
            Limits(pageSize, lastOffset),
            lang.last,
            isDisabled = lastOffset == current.offset
          )
      )
    )

  private def pageLink(
    base: Uri,
    to: Limits,
    text: String,
    isActive: Boolean = false,
    isDisabled: Boolean = false
  ) =
    val params = Map(Limits.Limit -> s"${to.limit}", Limits.Offset -> s"${to.offset}")
    val call = base.withQueryParams(params)
    val liClass = if isDisabled then "disabled" else ""
    val _ = if isActive then "todo" else "todo"
    li(cls := classes("page-item", liClass))(a(cls := "page-link", href := call)(text))
