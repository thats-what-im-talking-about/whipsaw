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
import twita.dominion.api.DomainObjectGroup

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

trait WorkItem[Desc] extends DomainObject[EventId, WorkItem[Desc]] {
  override type AllowedEvent = WorkItem.Event
  override type ObjectId = WorkItemId

  /**
    * @return If this item needs to be processed, this method returns the earliest time at which the item should be
    *         run.  This is how delays in scheduling are handled.  If the item is completed, runAt will be None and
    *         the scheduler will no longer pick up this item.
    */
  def runAt: Option[Instant]

  /**
    * @return Implementer-provided description of this work item.
    */
  def description: Desc
}

object WorkItem {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
}

trait WorkItems[Desc] extends DomainObjectGroup[EventId, WorkItem[Desc]] {
  override type AllowedEvent = WorkItems.Event
}

object WorkItems {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
}
