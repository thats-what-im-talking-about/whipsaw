package twita.whipsaw.api

import java.time.Instant
import java.util.UUID

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject

case class WorkItemId(value: String) extends AnyVal
object WorkItemId {
  def apply(): WorkItemId = WorkItemId(UUID.randomUUID().toString)
  implicit val fmt = new Format[WorkItemId] {
    override def reads(json: JsValue): JsResult[WorkItemId] = json match {
      case JsString(id) => JsSuccess(WorkItemId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: WorkItemId): JsValue = JsString(o.value)
  }
}

trait WorkItem extends DomainObject[EventId, WorkItem] {
  override type AllowedEvent = WorkItem.Event
  override type ObjectId = WorkItemId

  def runAt: Instant
}

object WorkItem {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
}
