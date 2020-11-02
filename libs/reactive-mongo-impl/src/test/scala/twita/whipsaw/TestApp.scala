package twita.whipsaw

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.libs.json.Json
import twita.dominion.api.DomainObjectGroup.byId
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.dominion.impl.reactivemongo.MongoContext
import twita.whipsaw.api.engine.RegisteredWorkload
import twita.whipsaw.api.engine.WorkloadRegistry
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.reactivemongo.impl.MongoWorkloadFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object TestApp {
  implicit val testAppActorSystem = ActorSystem("TestApp")
  implicit val materializer = Materializer(testAppActorSystem)

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

  val sampleMetadata = Metadata(
      new TestApp.SampleWorkloadScheduler(_: TestApp.SampleSchedulerParams)
    , new TestApp.SampleWorkItemProcessor(_: TestApp.SampleProcessorParams)
    , Seq("email")
    , "Sample"
  )

  object SampleWorkloadRegistry extends WorkloadRegistry {
    implicit val mongoContext = new DevMongoContextImpl()
    override def apply(rw: RegisteredWorkload)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]] =
      rw.factoryType match {
      case "Sample" => new MongoWorkloadFactory(sampleMetadata).get(byId(rw.id)).map {
        case Some(w) => w
        case None => throw new RuntimeException("no can do")
      }
    }
  }
}
