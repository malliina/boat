package tests

import com.malliina.boat.{Coord, Latitude, Longitude}
import org.scalatest.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BaseSuite extends FunSuite with CoordHelper {
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T], duration: Duration = 10.seconds): T = Await.result(f, duration)
}

trait CoordHelper {
  def newCoord(lng: Double, lat: Double) = Coord(Longitude(lng), Latitude(lat))
}
