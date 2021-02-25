package controllers

import com.malliina.boat.graph._
import com.malliina.boat.{EitherOps, Errors, RouteRequest}
import com.malliina.concurrent.Execution.cached
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction}

import scala.concurrent.Future

class GraphController(comps: ControllerComponents) extends AbstractController(comps) {
  val g = Graph.all

  def shortestRoute(srcLat: Double, srcLng: Double, destLat: Double, destLng: Double) =
    EssentialAction { rh =>
      val action = RouteRequest(srcLat, srcLng, destLat, destLng).map { req =>
        shortest(req)
      }.recover { err =>
        Action(BadRequest(Errors(err.message)))
      }
      action(rh)
    }

  def shortest(req: RouteRequest) = Action.async {
    findRoute(req).map { r =>
      r.map { result =>
        Ok(Json.toJson(result))
      }.recover {
        case NoRoute(f, t)     => NotFound(Errors(s"No route found from '$f' to '$t'."))
        case UnresolvedFrom(f) => BadRequest(Errors(s"Unresolvable from '$f'."))
        case UnresolvedTo(t)   => BadRequest(Errors(s"Unresolvable to '$t'."))
        case EmptyGraph        => InternalServerError(Errors("Graph engine not available."))
      }
    }
  }

  def findRoute(req: RouteRequest) = Future {
    g.shortest(req.from, req.to)
  }
}
