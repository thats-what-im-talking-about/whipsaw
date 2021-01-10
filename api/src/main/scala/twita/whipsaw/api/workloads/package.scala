package twita.whipsaw.api

import java.util.UUID

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import twita.dominion.api.IdGenerator

package object workloads {
  case class EventId(value: String) extends AnyVal
  object EventId {
    implicit val fmt = new Format[EventId] {
      override def reads(json: JsValue): JsResult[EventId] = json match {
        case JsString(id) => JsSuccess(EventId(id))
        case err => JsError(s"Expected a String but got ${err}")
      }

      override def writes(o: EventId): JsValue = JsString(o.value)
    }
  }

  trait EventIdGenerator extends IdGenerator[EventId] {
    override def generateId: EventId = EventId(UUID.randomUUID().toString)
  }
}
