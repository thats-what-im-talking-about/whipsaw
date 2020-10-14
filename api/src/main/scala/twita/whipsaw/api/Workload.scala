package twita.whipsaw.api

import java.util.UUID

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup

case class WorkloadId(value: String) extends AnyVal
object WorkloadId {
  def apply(): WorkloadId = WorkloadId(UUID.randomUUID().toString)
  implicit val fmt = new Format[WorkloadId] {
    override def reads(json: JsValue): JsResult[WorkloadId] = json match {
      case JsString(id) => JsSuccess(WorkloadId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: WorkloadId): JsValue = JsString(o.value)
  }
}

trait Workload extends DomainObject[EventId, Workload] {
  override type AllowedEvent = Workload.Event
  override type ObjectId = WorkloadId

  def name: String
}

object Workload {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
}

trait Workloads extends DomainObjectGroup[EventId, Workload] {
  override type AllowedEvent = Workloads.Event
}

object Workloads {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class Created(name: String) extends Event
  object Created { implicit val fmt = Json.format[Created] }
}
