package twita.whipsaw.play.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import controllers.AssetsFinder
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc.AbstractController
import play.api.mvc.ControllerComponents
import play.api.mvc.WebSocket
import twita.whipsaw.api.engine
import twita.whipsaw.app.workloads.MetadataRegistry
import twita.whipsaw.app.workloads.processors.AppenderParams
import twita.whipsaw.app.workloads.schdulers.ItemCountParams
import twita.whipsaw.monitor.MonitorActor

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class LaunchWorkloadRequest(name: String, items: Int)
object LaunchWorkloadRequest {
  implicit val fmt = Json.format[LaunchWorkloadRequest]
}

class HomeController(cc: ControllerComponents,
                     workloadDirector: engine.Director)(
  implicit assetsFinder: AssetsFinder,
  system: ActorSystem,
  mat: Materializer,
  executionContext: ExecutionContext
) extends AbstractController(cc) {

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def monitor = Action {
    Ok(views.html.monitor())
  }

  def websocket = WebSocket.accept[String, String] { implicit req =>
    ActorFlow.actorRef { out =>
      MonitorActor.props(workloadDirector, out)
    }
  }

  def launchWorkload = Action.async(cc.parsers.json) { implicit req =>
    req.body
      .validate[LaunchWorkloadRequest]
      .fold(
        invalid => Future.successful(BadRequest(invalid.toString)),
        parsed => {
          val factory = workloadDirector.registry(MetadataRegistry.sample)
          for {
            workload <- factory match {
              case Right(f) => f.apply(f.Created(
                parsed.name,
                ItemCountParams(parsed.items),
                AppenderParams("PrOcEsSeD")
              ))
              case Left(err) => Future.failed(new RuntimeException(err.msg))
            }
          } yield Ok(Json.toJson(workload.id))
        }
      )
  }

  def start(numItems: Int, name: String) = Action.async { implicit request =>
    val factory = workloadDirector.registry(MetadataRegistry.sample)
    for {
      workload <- factory match {
        case Right(f) => f(f.Created(name, ItemCountParams (numItems), AppenderParams ("PrOcEsSeD")))
        case Left(err) => Future.failed(new RuntimeException(err.msg))
      }
    } yield Ok(Json.toJson(workload.id))
  }
}
