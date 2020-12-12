package twita.whipsaw.play 

import play.api._
import play.api.ApplicationLoader.Context
import _root_.controllers.AssetsComponents
import play.filters.HttpFiltersComponents
import router.Routes
import twita.whipsaw.play.controllers.HomeController

class WhipsawApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    new WhipsawComponents(context).application
  }
}

class WhipsawComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents
{
  lazy val homeController = new HomeController(controllerComponents)(assetsFinder, actorSystem, materializer)
  lazy val router = new Routes(httpErrorHandler, homeController, assets, "/")
}