package com.malliina.boat

object MapboxStyles:
  // "rajoitusalue_a" is probably no longer used
  val LimitLayerIds = Seq("limit-areas")
  val FairwayAreaLayers = Seq("fairway-areas")
  val DepthAreaLayers = Seq(1, 2, 3, 4).map(idx => s"depth-area-$idx")

  val AisTrailLayer = "ais-vessels-trails"
  val AisVesselLayer = "ais-vessels"
  val AisVesselIcon = "boat-resized-opt-30"

  val aisLayers = Seq(AisVesselLayer, AisTrailLayer)
  val fairwayLayers = Seq("fairways")

  val marksLayers = Seq("marks", "marks-special", "traffic-signs")

  val clickableLayers = marksLayers ++ Seq(AisVesselLayer) ++ fairwayLayers
