package twita.whipsaw.play 

import play.api._
import play.api.ApplicationLoader.Context
import play.api.libs.json.Json
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

case class AppAttributes(projectId: Option[Long], orgId: Option[Long])
object AppAttributes { implicit val fmt = Json.format[AppAttributes] }

class WhipsawComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
    with WorkloadReactiveMongoComponents[AppAttributes]
    with AppRegistry[AppAttributes]
{
  implicit val attrFmt = AppAttributes.fmt

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

class WorkloadDirectorActor[Attr](director: engine.Director[Attr])(implicit m: Materializer) extends Actor {
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
