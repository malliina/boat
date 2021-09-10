import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

package object tests:
  def await[T](f: Future[T]): T = Await.result(f, 20.seconds)
