import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

package object tests {

  implicit def durInt(i: Int): DurationInt = new DurationInt(i)

  def await[T](f: Future[T]): T = Await.result(f, Duration(3, TimeUnit.SECONDS))

}
