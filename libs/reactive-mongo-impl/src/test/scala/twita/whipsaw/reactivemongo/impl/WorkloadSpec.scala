package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.ItemResult
import twita.whipsaw.api.RegisteredScheduler
import twita.whipsaw.api.WorkItemProcessor
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads
import twita.whipsaw.reactivemongo.impl.testapp.ProcessorRegistryEntry
import twita.whipsaw.reactivemongo.impl.testapp.SampleProcessorParams
import twita.whipsaw.reactivemongo.impl.testapp.SampleSchedulerParams
import twita.whipsaw.reactivemongo.impl.testapp.SchedulerRegistryEntry

import scala.concurrent.Future

package testapp {
  import enumeratum._
  import play.api.libs.json.Format
  import play.api.libs.json.JsObject
  import play.api.libs.json.JsResult
  import play.api.libs.json.JsString
  import play.api.libs.json.JsSuccess
  import play.api.libs.json.JsValue
  import play.api.libs.json.Json
  import play.api.libs.json.OFormat
  import twita.dominion.impl.reactivemongo.MongoContext
  import twita.whipsaw.api.RegisteredProcessor
  import twita.whipsaw.api.RegisteredScheduler
  import twita.whipsaw.api.ScheduleCompletion
  import twita.whipsaw.api.WorkloadScheduler

  import scala.concurrent.ExecutionContext

  case class SamplePayload(
      email: String
    , target: String
    , touchedCount: Int = 0
  )
  object SamplePayload { implicit val fmt = Json.format[SamplePayload] }

  // create a holder for the arguments that are going to the scheduler
  case class SampleSchedulerParams(numItems: Int)
  object SampleSchedulerParams { implicit val fmt = Json.format[SampleSchedulerParams] }

  // create a scheduler
  class SampleWorkloadScheduler(p: SampleSchedulerParams) extends WorkloadScheduler[SamplePayload] {
    override def schedule(): Iterator[SamplePayload] = Range(1, p.numItems).iterator.map { index =>
      SamplePayload(s"bplawler+${index}@gmail.com", s"item #${index}")
    }
  }

  // create a holder for processor parameters
  case class SampleProcessorParams(msgToAppend: String)
  object SampleProcessorParams { implicit val fmt = Json.format[SampleProcessorParams] }

  // create a processor
  class SampleWorkItemProcessor(p: SampleProcessorParams) extends WorkItemProcessor[SamplePayload] {
    override def process(payload: SamplePayload): Future[(ItemResult, SamplePayload)] =
      Future.successful((
        ItemResult.Done, payload.copy(target = List(payload.target, p.msgToAppend).mkString(":").toUpperCase())
      ))
  }

  // Create a Workload Processor Registry
  sealed trait ProcessorRegistryEntry[PParams, Payload] extends EnumEntry with RegisteredProcessor[PParams, Payload]
  object ProcessorRegistryEntry extends Enum[ProcessorRegistryEntry[_, _]] with PlayJsonEnum[ProcessorRegistryEntry[_, _]] {
    override val values = findValues

    case class Sample() extends ProcessorRegistryEntry[SampleProcessorParams, SamplePayload] {
      override def withParams(p: SampleProcessorParams): WorkItemProcessor[SamplePayload] = new SampleWorkItemProcessor(p)
    }
    object Sample { implicit val fmt = new Format[Sample] {
      override def writes(o: Sample): JsValue = JsString(o.entryName)
      override def reads(json: JsValue): JsResult[Sample] = json match {
        case JsString(str) => JsSuccess(Sample())
      }
    }}
  }

  // Create a Workload Scheduler Registry
  sealed trait SchedulerRegistryEntry[SParams, Payload] extends EnumEntry with RegisteredScheduler[SParams, Payload]
  object SchedulerRegistryEntry extends Enum[SchedulerRegistryEntry[_,_]] with PlayJsonEnum[SchedulerRegistryEntry[_,_]] {
    override val values = findValues

    case class Sample() extends SchedulerRegistryEntry[SampleSchedulerParams, SamplePayload] {
      override def withParams(p: SampleSchedulerParams): WorkloadScheduler[SamplePayload] = new SampleWorkloadScheduler(p)
    }
    object Sample { implicit val fmt = new Format[Sample] {
      override def writes(o: Sample): JsValue = JsString(o.entryName)
      override def reads(json: JsValue): JsResult[Sample] = json match {
        case JsString(str) => JsSuccess(Sample())
      }
    }}
  }

  // create a workload factory out of these types
  class SampleMongoWorkloadFactory(implicit executionContext: ExecutionContext, mongoContext: MongoContext)
    extends MongoWorkloads[SamplePayload, SampleSchedulerParams, SchedulerRegistryEntry.Sample, SampleProcessorParams, ProcessorRegistryEntry.Sample] {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }
}

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val workloadFactory = new testapp.SampleMongoWorkloadFactory
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(
        Workloads.Created(
            name = "Sample Workload"
          , scheduler = SchedulerRegistryEntry.Sample()
          , schedulerParams = SampleSchedulerParams(10)
          , processor = ProcessorRegistryEntry.Sample()
          , processorParams = SampleProcessorParams("PrOcEsSeD")
        )
      )
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  //def foo[Payload](factory: MongoWorkloads[_], )
  /*
  "work items" should "be added to workloads via apply()" in {
    val factory = new test.SampleMongoWorkloadFactory()
    for {
      workload <- factory.get(DomainObjectGroup.byId(workloadId))
      createdItem <- workload.get.workItems(
        WorkItems.WorkItemAdded(test.SamplePayload("bplawler@gmail.com", "string to be processed"))
      )
    } yield assert(createdItem.id != null)
  }

  "workload processing" should "process the runnable workload" in {
    val workloadFactory = new test.SampleMongoWorkloadFactory

    for {
      _ <- workloadFactory.process(workloadId, engine)
      items <- workloadFactory.get(DomainObjectGroup.byId(workloadId)).flatMap {
        case None => Future.failed(new RuntimeException("huh?"))
        case Some(workload) => workload.workItems.list
      }
    } yield assert(items.head.payload.target == "STRING TO BE PROCESSED")
  }
  */
}

