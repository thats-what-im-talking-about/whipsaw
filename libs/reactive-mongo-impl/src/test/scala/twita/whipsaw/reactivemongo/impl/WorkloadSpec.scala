package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.ItemResult
import twita.whipsaw.api.WorkItem.Processed
import twita.whipsaw.api.WorkItemProcessor
import twita.whipsaw.api.WorkItems.WorkItemAdded
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads
import twita.whipsaw.reactivemongo.impl.testapp.ProcessorRegistryEntry
import twita.whipsaw.reactivemongo.impl.testapp.SampleProcessorParams
import twita.whipsaw.reactivemongo.impl.testapp.SampleSchedulerParams
import twita.whipsaw.reactivemongo.impl.testapp.SchedulerRegistryEntry

import scala.concurrent.Future

package testapp {
  import enumeratum._
  import play.api.libs.json.Json
  import twita.dominion.impl.reactivemongo.MongoContext
  import twita.whipsaw.api.RegisteredProcessor
  import twita.whipsaw.api.RegisteredScheduler
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
    override def schedule(): Iterator[SamplePayload] = Range(0, p.numItems).iterator.map { index =>
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
        ItemResult.Done, payload.copy(
            target = List(payload.target, p.msgToAppend).mkString(":").toUpperCase()
          , touchedCount = payload.touchedCount + 1
        )
      ))
  }

  // Create a Workload Processor Registry
  sealed trait ProcessorRegistryEntry extends EnumEntry with RegisteredProcessor
  object ProcessorRegistryEntry extends Enum[ProcessorRegistryEntry] with PlayJsonEnum[ProcessorRegistryEntry] {
    override val values = findValues

    case object Sample extends ProcessorRegistryEntry {
      override def apply(params: Any): WorkItemProcessor[_] = params match {
        case p: SampleProcessorParams => new SampleWorkItemProcessor(p)
      }
    }
  }

  // Create a Workload Scheduler Registry
  sealed trait SchedulerRegistryEntry extends EnumEntry with RegisteredScheduler
  object SchedulerRegistryEntry extends Enum[SchedulerRegistryEntry] with PlayJsonEnum[SchedulerRegistryEntry] {
    override val values = findValues

    case object Sample extends SchedulerRegistryEntry {
      override def apply(params: Any): WorkloadScheduler[SamplePayload] = params match {
        case p: SampleSchedulerParams => new SampleWorkloadScheduler(p)
      }
    }
  }

  // create a workload factory out of these types
  class SampleMongoWorkloadFactory(implicit executionContext: ExecutionContext, mongoContext: MongoContext)
    extends MongoWorkloads[
        SamplePayload
      , SampleSchedulerParams
      , SchedulerRegistryEntry
      , SampleProcessorParams
      , ProcessorRegistryEntry] {
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
          , scheduler = SchedulerRegistryEntry.Sample: SchedulerRegistryEntry
          , schedulerParams = SampleSchedulerParams(10)
          , processor = ProcessorRegistryEntry.Sample: ProcessorRegistryEntry
          , processorParams = SampleProcessorParams("PrOcEsSeD")
        )
      )
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  "work items" should "be scheduled properly with the workload scheduler" in {
    val factory = new testapp.SampleMongoWorkloadFactory()
    for {
      workload <- factory.get(DomainObjectGroup.byId(workloadId))
      scheduler = workload.get.scheduler
      items = scheduler.schedule().toList
      addedItems <- Future.traverse(items) {item => workload.get.workItems(WorkItemAdded(item))}
    } yield assert(addedItems.size == 10)
  }

  "workload processing" should "process the runnable workload" in {
    val workloadFactory = new testapp.SampleMongoWorkloadFactory

    for {
      workload <- workloadFactory.get(DomainObjectGroup.byId(workloadId))
      processor = workload.get.processor
      items <- workload.get.workItems.runnableItemList
      processedItems <- Future.traverse(items) { item =>
        for {
          (itemResult, updatedPayload) <- processor.process(item.payload)
          result <- item(Processed(updatedPayload, itemResult))
        } yield result
      }
    } yield assert(processedItems.size == 10)
  }
}

