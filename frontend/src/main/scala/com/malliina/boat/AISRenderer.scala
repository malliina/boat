package com.malliina.boat

import com.malliina.boat.AISRenderer.{AisTrailLayer, AisVesselLayer, MaxTrailLength}
import com.malliina.json.Parsing.as
import com.malliina.mapbox.{MapboxMap, MapboxPopup, PopupOptions}
import com.malliina.values.ErrorMessage

object AISRenderer:
  val AisTrailLayer = MapboxStyles.AisTrailLayer
  val AisVesselLayer = MapboxStyles.AisVesselLayer

  val MaxTrailLength = 100

class AISRenderer(val map: MapboxMap, val log: BaseLogger = BaseLogger.console):
  val utils = GeoUtils(map, log)
  // How much memory does this consume, assume 5000 Mmsis?
  private var vessels = Map.empty[Mmsi, List[VesselInfo]]

  val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))

  def info(mmsi: Mmsi): Either[ErrorMessage, VesselInfo] =
    vessels.get(mmsi).flatMap(_.headOption).toRight(ErrorMessage(s"MMSI not found: '$mmsi'."))

  def search(text: String): List[VesselInfo] =
    vessels.values
      .flatMap(_.headOption)
      .filter(_.name.name.toLowerCase.contains(text.toLowerCase))
      .toList

  private def locationData = FeatureCollection(
    vessels.values
      .flatMap(_.headOption)
      .map: v =>
        val heading = v.heading.getOrElse(v.cog.toInt)
//      log.info(s"Heading $heading bearing ${map.bearing.toInt} rotation $bearing")
        Feature.point(v.coord, VesselProps(v.mmsi, v.name, heading))
      .toList
  )

  private def trailData = FeatureCollection(
    vessels.values
      .map(_.drop(1))
      .map: trail =>
        Feature.line(trail.map(_.coord))
      .toList
  )

  def onAIS(messages: Seq[VesselInfo]): Unit =
    val updated = messages
      .map: msg =>
        msg.mmsi -> (msg :: vessels.getOrElse(msg.mmsi, Nil)).take(MaxTrailLength)
      .toMap
    vessels = vessels ++ updated
    val iconUpdateOutcome = utils.updateOrSet(
      Layer.symbol(
        AisVesselLayer,
        locationData,
        ImageLayout(utils.boatIconId, `icon-size` = 1, Option(Seq("get", VesselInfo.HeadingKey)))
      )
    )
    if iconUpdateOutcome == Outcome.Added then initHover(AisVesselLayer)
    utils.updateOrSet(
      Layer.line(AisTrailLayer, trailData, minzoom = Option(10))
    )

  private def initHover(id: String): Unit = map.onHover(id)(
    in =>
      in.features
        .as[Seq[Feature]]
        .map: fs =>
          fs.flatMap(_.props.as[VesselProps].toOption)
            .headOption
            .foreach: vessel =>
              boatPopup.showText(vessel.name.name, in.lngLat, map)
        .left
        .map: err =>
          log.info(s"JSON error '$err'."),
    _ => boatPopup.remove()
  )
