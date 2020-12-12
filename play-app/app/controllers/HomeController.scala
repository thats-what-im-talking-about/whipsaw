package twita.whipsaw.play.controllers

import akka.Done
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.CompletionStrategy
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import controllers.AssetsFinder
import play.api.mvc.AbstractController
import play.api.mvc.ControllerComponents
import play.api.mvc.WebSocket


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
class HomeController (cc: ControllerComponents) (implicit assetsFinder: AssetsFinder, system: ActorSystem, mat: Materializer)
  extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  val src: Source[String, ActorRef] = Source.actorRef(
    completionMatcher = { case Done => CompletionStrategy.immediately },
    failureMatcher = PartialFunction.empty,
    bufferSize = 100,
    overflowStrategy = OverflowStrategy.dropHead
  )

  lazy val (actor, newSrc) = src.preMaterialize()

  def websocket = WebSocket.accept[String, String] { implicit req =>
    val in = Sink.foreach[String](s => println(s))
    val out = newSrc.map { s => println(s"received ${s}"); s }
    Flow.fromSinkAndSource(in, out)
  }

  def send(s: String) = Action { implicit req =>
    actor ! s
    Ok(s"sent ${s}")
  }
}
