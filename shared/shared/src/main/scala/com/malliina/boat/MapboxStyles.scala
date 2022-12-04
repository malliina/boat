package com.malliina.boat

object MapboxStyles:
  // "rajoitusalue_a" is probably no longer used
  val LimitLayerIds = Seq("rajoitusalue", "rajoitusalue_a")
  val FairwayAreaId = "vaylaalueet"

  val AisTrailLayer = "ais-vessels-trails"
  val AisVesselLayer = "ais-vessels"
  val AisVesselIcon = "boat-resized-opt-30"

  val aisLayers = Seq(AisVesselLayer, AisTrailLayer)
  val fairwayLayers = Seq("vaylat", "fairways")

  val marksLayers = Seq(
    "marks",
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
    "cardinal-north"
  )

  val clickableLayers = marksLayers ++ Seq(AisVesselLayer) ++ fairwayLayers
