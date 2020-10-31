package twita.whipsaw

import play.api.libs.json.Json
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object TestApp {
  case class SamplePayload(
      email: String
    , target: String
    , touchedCount: Int = 0
  )
  object SamplePayload { implicit val fmt = Json.format[SamplePayload] }

  // create a holder for the arguments that are going to the scheduler
  case class SampleSchedulerParams(numItems: Int)
  object SampleSchedulerParams { implicit val fmt = Json.format[SampleSchedulerParams] }

  // created an alternative just to convince myself that the compiler would be unhappy.
  case class WrongSampleSchedulerParams(numItems: Int)
  object WrongSampleSchedulerParams { implicit val fmt = Json.format[WrongSampleSchedulerParams] }

  // create a scheduler
  class SampleWorkloadScheduler(p: SampleSchedulerParams) extends Scheduler[SamplePayload] {
    override def schedule()(implicit ec: ExecutionContext): Future[Iterator[SamplePayload]] = Future {
      Range(0, p.numItems).iterator.map { index =>
        SamplePayload(s"bplawler+${index}@gmail.com", s"item #${index}")
      }
    }
  }

  // create a holder for processor parameters
  case class SampleProcessorParams(msgToAppend: String)
  object SampleProcessorParams { implicit val fmt = Json.format[SampleProcessorParams] }

  // create a processor
  class SampleWorkItemProcessor(p: SampleProcessorParams) extends Processor[SamplePayload] {
    override def process(payload: SamplePayload): Future[(ItemResult, SamplePayload)] =
      Future.successful((
        ItemResult.Done, payload.copy(
        target = List(payload.target, p.msgToAppend).mkString(":").toUpperCase()
        , touchedCount = payload.touchedCount + 1
      )
      ))
  }
}
