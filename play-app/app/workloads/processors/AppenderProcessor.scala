package twita.whipsaw.app.workloads.processors

import play.api.libs.json.Json
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.app.workloads.payloads.StringPayload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

case class AppenderParams(msgToAppend: String)
object AppenderParams { implicit val fmt = Json.format[AppenderParams] }

class AppenderToUpperProcessor(params: AppenderParams) extends Processor[StringPayload] {
  override def process(payload: StringPayload)(implicit executionContext: ExecutionContext): Future[(ItemResult, StringPayload)] = Future {
    Thread.sleep(100)

    if(Random.nextInt(100) < 20) (ItemResult.Retry(new RuntimeException()), payload)
    else
      (ItemResult.Done, payload.copy(
          target = List(payload.target, params.msgToAppend).mkString(":").toUpperCase()
        , touchedCount = payload.touchedCount + 1
      ))
  }
}


