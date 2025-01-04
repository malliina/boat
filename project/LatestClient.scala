import LatestClient.Files
import com.malliina.http.{FullUrl, OkClient}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sbt.util.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object LatestClient {
  case class Files(latest: Option[FullUrl])

  object Files {
    implicit val json: Codec[Files] = deriveCodec[Files]
  }

  def default(log: Logger) = new LatestClient(OkClient.default, log)
}

class LatestClient(http: OkClient, log: Logger) {
  val url = FullUrl.https("www.boat-tracker.com", "/files")

  def latest: Option[FullUrl] =
    try
      Await.result(files, 10.seconds).latest
    catch {
      case e: Exception =>
        log.error(s"Timed out fetching files from '$url'. $e")
        None
    }

  def files: Future[Files] = http.getAs[Files](url).recover { case t =>
    log.error(s"Failed to fetch files from '$url'. $t")
    Files(None)
  }
}
