package com.malliina.boat.html

import com.malliina.assets.HashedAssets
import com.malliina.boat.html.AssetsSource.prefix
import org.http4s.Uri
import org.http4s.implicits.uri

trait AssetsSource:
  def at(file: String): Uri

object AssetsSource:
  val prefix = uri"/assets"

  def apply(isProd: Boolean): AssetsSource =
    if isProd then HashedAssetsSource
    else DirectAssets

object DirectAssets extends AssetsSource:
  override def at(file: String): Uri =
    prefix.addPath(file)

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    prefix.addPath(optimal)
