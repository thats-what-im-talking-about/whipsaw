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
import twita.whipsaw.api.engine.MonitorActor
import twita.whipsaw.app.workloads.MetadataRegistry
import twita.whipsaw.app.workloads.processors.AppenderParams
import twita.whipsaw.app.workloads.schdulers.ItemCountParams

import scala.concurrent.ExecutionContext


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
class HomeController(cc: ControllerComponents, workloadDirector: engine.Director)(
  implicit assetsFinder: AssetsFinder, system: ActorSystem, mat: Materializer, executionContext: ExecutionContext
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
    Ok(views.html.monitor("Welcome to the Whipsaw Workload Monitor"))
  }

  def websocket = WebSocket.accept[String, String] { implicit req =>
    ActorFlow.actorRef { out => MonitorActor.props(workloadDirector, out) }
  }

  def start(numItems: Int, name: String) = Action.async { implicit request =>
    val factory = workloadDirector.registry(MetadataRegistry.sample)

    for {
      workload <- factory(factory.Created(name, ItemCountParams(numItems), AppenderParams("PrOcEsSeD")))
    } yield Ok(Json.toJson(workload.id))
  }
}
