package twita.whipsaw.play 

import play.api._
import play.api.ApplicationLoader.Context
import _root_.controllers.AssetsComponents
import akka.actor.Actor
import akka.actor.Cancellable
import akka.actor.Props
import akka.stream.Materializer
import play.filters.HttpFiltersComponents
import router.Routes
import twita.whipsaw.api.engine
import twita.whipsaw.app.workloads.AppRegistry
import twita.whipsaw.impl.reactivemongo.WorkloadReactiveMongoComponents
import twita.whipsaw.play.controllers.HomeController

import scala.concurrent.duration._

class WhipsawApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    new WhipsawComponents(context).application
  }
}

class WhipsawComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with WorkloadReactiveMongoComponents
    with AppRegistry
{
  lazy val homeController =
    new HomeController(
        controllerComponents
      , workloadDirector
    )(
        assetsFinder
      , actorSystem
      , materializer
      , executionContext
    )
  lazy val router = new Routes(httpErrorHandler, homeController, assets, "/")

  val workloadDirectorActor = actorSystem.actorOf(Props(new WorkloadDirectorActor(workloadDirector)))
}

class WorkloadDirectorActor(director: engine.Director)(implicit m: Materializer) extends Actor {
  implicit def executionContext = context.system.dispatcher

  def timer = context.system.scheduler.scheduleAtFixedRate(5.second, 5.second, self, WorkloadDirectorActor.LaunchRunnables)

  override def receive: Receive = polling(timer)

  def polling(timer: Cancellable): Receive = {
    case WorkloadDirectorActor.LaunchRunnables =>
      println("search for runnable workloads")
      director.delegateRunnableWorkloads()
  }
}

object WorkloadDirectorActor {
  case object LaunchRunnables
}
