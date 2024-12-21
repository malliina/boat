package com.malliina.boat.parking

import io.circe.{Codec, Decoder}

case class ParkingProps(
  id: Option[Int],
  text: Option[String],
  validity: Option[String],
  duration: Option[String],
  residentialParkingSign: Option[String],
  propsType: Option[String],
  updatedDate: Option[String]
):
  def label = text.orElse(propsType)

object ParkingProps:
  // Decodes the empty string to None
  given Decoder[Option[String]] = Decoder.decodeOption[String].map(opt => opt.filter(_.nonEmpty))

  /** @param luokka_nimi
    *   e.g. "Maksullinen pysäköinti"
    * @param voimassaolo
    *   e.g. "9-21, (9-18)"
    * @param kesto
    *   e.g. "4 h"
    * @param asukaspysakointitunnus
    *   e.g. "F"
    * @param tyyppi
    *   e.g. "Moottoripyörä" (wtf?)
    * @param paivitetty_tietopalveluun
    *   e.g. "2024-12-20"
    */
  private case class ParkingPropsJson(
    id: Option[Int],
    luokka_nimi: Option[String],
    voimassaolo: Option[String],
    kesto: Option[String],
    asukaspysakointitunnus: Option[String],
    tyyppi: Option[String],
    paivitetty_tietopalveluun: Option[String]
  ) derives Codec.AsObject

  given Decoder[ParkingProps] = Decoder[ParkingPropsJson].map: json =>
    ParkingProps(
      json.id,
      json.luokka_nimi,
      json.voimassaolo,
      json.kesto,
      json.asukaspysakointitunnus,
      json.tyyppi,
      json.paivitetty_tietopalveluun
    )
