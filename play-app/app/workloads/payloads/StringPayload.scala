package twita.whipsaw.app.workloads.payloads

import play.api.libs.json.Json

case class StringPayload(
    email: String
  , target: String
  , touchedCount: Int = 0
)
object StringPayload { implicit val fmt = Json.format[StringPayload] }
