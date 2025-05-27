package com.malliina.boat.html

import com.malliina.assets.{FileAssets, HashedAssets}
import com.malliina.boat.FrontKeys.*
import com.malliina.boat.html.BoatImplicits.given
import com.malliina.boat.http.{Limits, TracksQuery}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{AppConf, Boat, BuildInfo, Car, Coord, FullTrack, Lang, SourceType, TrackRef, TracksBundle, UserBoats, UserInfo}
import com.malliina.html.HtmlImplicits.given
import com.malliina.html.HtmlTags.{cssLink, deviceWidthViewport, titleTag}
import com.malliina.http.{CSRFConf, CSRFToken, FullUrl}
import com.malliina.live.LiveReload
import org.http4s.Uri
import scalatags.Text.all.*

import scala.language.implicitConversions

object BoatHtml:
  def fromBuild(sourceType: SourceType, csrf: CSRFConf): BoatHtml =
    default(BuildInfo.isProd, sourceType, csrf)

  private def chooseFavicon(sourceType: SourceType) =
    if sourceType == SourceType.Vehicle then FileAssets.img.favicon_car_svg
    else FileAssets.img.favicon_boat_png

  def faviconPath(sourceType: SourceType) =
    AssetsSource.prefix.addPath(chooseFavicon(sourceType))

  def default(isProd: Boolean, sourceType: SourceType, csrf: CSRFConf): BoatHtml =
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    val pageTitle =
      if sourceType == SourceType.Vehicle then AppConf.CarName
      else s"${AppConf.Name} - Free nautical charts for Finland"
    val appScripts =
      if isProd then Seq(FileAssets.frontend_js)
      else Seq(FileAssets.frontend_js, FileAssets.frontend_loader_js, FileAssets.main_js)
    BoatHtml(
      appScripts,
      externalScripts,
      Seq(FileAssets.frontend_css, FileAssets.fonts_css, FileAssets.styles_css),
      AssetsSource(isProd),
      chooseFavicon(sourceType),
      pageTitle,
      csrf
    )

class BoatHtml(
  jsFiles: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource,
  favicon: String,
  pageTitle: String,
  csrfConf: CSRFConf
):
  val reverse = Reverse

  def carsAndBoats(user: UserInfo, cars: Seq[Car], token: CSRFToken) =
    page(PageConf(BoatsPage(user, cars, token, csrfConf), Seq(BoatsClass)))

  def editDevice(user: UserInfo, boat: Boat, csrfToken: CSRFToken, csrfConf: CSRFConf) =
    page(PageConf(BoatsPage.edit(user, boat, csrfToken, csrfConf), Seq(BoatsClass)))

  def tracks(user: UserInfo, data: TracksBundle, query: TracksQuery, lang: BoatLang): Frag =
    page(PageConf(TracksPage(user, data, query, lang), Seq(StatsClass)))

  def signIn(lang: Lang) = page(
    PageConf(SignInPage(lang.settings))
  )

  def list(track: FullTrack, current: Limits, lang: BoatLang) =
    page(PageConf(SentencesPage(track, current, lang), Seq(FormsClass)))

  def chart(track: TrackRef, lang: BoatLang) =
    page(Charts.chart(track, lang))

  def privacyPolicy = page(PageConf(PrivacyPolicy.page))

  def map(ub: UserBoats, center: Option[Coord] = None) =
    page(
      PageConf(
        MapPage(ub, center),
        bodyClasses = Seq(s"$MapClass $AboutClass")
      )
    )

  def page(pageConf: PageConf): Frag =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        meta(
          name := "description",
          content := "Free nautical charts for Finland with live AIS tracking."
        ),
        meta(
          name := "keywords",
          content := "charts, nautical, boat, tracking, ais, live, vessels, marine"
        ),
        titleTag(pageTitle),
        deviceWidthViewport,
        StructuredData.appStructuredData,
        StructuredData.appLinkMetadata,
        link(rel := "icon", `type` := "image/png", href := inlineOrAsset(favicon)),
        cssFiles.map: file =>
          cssLink(versioned(file))
      ),
      body(cls := pageConf.bodyClasses.mkString(" "))(
        pageConf.content,
        jsFiles.map: jsFile =>
          script(`type` := "text/javascript", src := versioned(jsFile)),
        externalScripts.map: url =>
          script(src := url, defer)
      )
    )

  private def inlineOrAsset(file: String) =
    HashedAssets.dataUris.getOrElse(file, versioned(file).toString)
  private def versioned(file: String): Uri = assets.at(file)
