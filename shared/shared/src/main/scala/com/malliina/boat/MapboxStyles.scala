package com.malliina.boat

object MapboxStyles {
  val AisTrailLayer = "ais-vessels-trails"
  val AisVesselLayer = "ais-vessels"
  val AisVesselIcon = "boat-resized-opt-30"

  val aisLayers = Seq(AisVesselLayer, AisTrailLayer)

  val marksLayers = Seq(
    "marks-turvavesi",
    "marks-kummeli",
    "marks-sektoriloisto",
    "marks-speed-limit",
    "marks-merimajakka",
    "marks-tunnusmajakka",
    "marks-no-waves",
    "marks-linjamerkki",
    "marks-tutka",
    "lateral-green",
    "lateral-red",
    "cardinal-west",
    "cardinal-south",
    "cardinal-east",
    "cardinal-north",
  )

  val clickableLayers = marksLayers ++ Seq(AisVesselLayer)
}