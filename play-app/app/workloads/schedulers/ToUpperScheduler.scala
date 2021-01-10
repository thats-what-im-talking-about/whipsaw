package twita.whipsaw.app.workloads.schdulers

import play.api.libs.json.Json
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.app.workloads.payloads.StringPayload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ItemCountParams(numItems: Int)
object ItemCountParams { implicit val fmt = Json.format[ItemCountParams] }

class ToUpperScheduler(params: ItemCountParams) extends Scheduler[StringPayload] {
  override def schedule()(implicit ec: ExecutionContext): Future[Iterator[StringPayload]] = Future {
    Range(0, params.numItems).iterator.map { index =>
      StringPayload(s"bplawler+${index}@gmail.com", s"item #${index}")
    }
  }
}

