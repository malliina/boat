package controllers

import akka.actor.ActorSystem
import akka.stream.scaladsl.StreamConverters
import com.malliina.boat.S3Client
import com.malliina.play.actions.Actions
import com.malliina.play.http.FullUrls
import controllers.FileController.BlockingActions
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.mvc._

object FileController {

  class BlockingActions(as: ActorSystem, val parser: BodyParser[AnyContent])
    extends Actions.SyncAction(as)

}

class FileController(s3: S3Client, actions: BlockingActions, comps: ControllerComponents) extends AbstractController(comps) {
  def list = actions { rh =>
    val urls = s3.files().map { summary =>
      FullUrls(routes.FileController.download(summary.getKey), rh)
    }

    Ok(Json.obj("files" -> urls))
  }

  def download(file: String) = actions {
    val obj = s3.download(file)
    val meta = obj.getObjectMetadata
    val src = StreamConverters.fromInputStream(() => obj.getObjectContent)
    Ok.sendEntity(HttpEntity.Streamed(src, Option(meta.getContentLength), Option(meta.getContentType)))
  }
}
