package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.WorkItem.Processed
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.reactivemongo.impl.testapp.SampleProcessorParams
import twita.whipsaw.reactivemongo.impl.testapp.SampleSchedulerParams

import scala.concurrent.Future

package testapp {
  import play.api.libs.json.Json
  import twita.whipsaw.api.workloads.ItemResult
  import twita.whipsaw.api.workloads.Scheduler

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
    override def schedule(): Iterator[SamplePayload] = Range(0, p.numItems).iterator.map { index =>
      SamplePayload(s"bplawler+${index}@gmail.com", s"item #${index}")
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

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val metadata = Metadata(new testapp.SampleWorkloadScheduler(_:SampleSchedulerParams), new testapp.SampleWorkItemProcessor(_:SampleProcessorParams))
  val workloadFactory = new MongoWorkloadFactory(metadata)
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(
        workloadFactory.Created(
            name = "Sample Workload"
          , schedulerParams = SampleSchedulerParams(10)
          , processorParams = SampleProcessorParams("PrOcEsSeD")
        )
      )
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  "work items" should "be scheduled properly with the workload scheduler" in {
    val factory = new MongoWorkloadFactory(metadata)
    for {
      workload <- factory.get(DomainObjectGroup.byId(workloadId)).map(_.get)
      scheduler = workload.scheduler
      items = scheduler.schedule().toList
      itemFactory = workload.workItems
      addedItems <- Future.traverse(items) {item => itemFactory(itemFactory.WorkItemAdded(item))}
    } yield assert(addedItems.size == 10)
  }

  "workload processing" should "process the runnable workload" in {
    val factory = new MongoWorkloadFactory(metadata)

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

