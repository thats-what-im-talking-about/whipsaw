package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.Future

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val metadata = Metadata(
      new TestApp.SampleWorkloadScheduler(_: TestApp.SampleSchedulerParams)
    , new TestApp.SampleWorkItemProcessor(_: TestApp.SampleProcessorParams)
    , Seq("email")
  )
  val workloadFactory = new MongoWorkloadFactory(metadata)
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(
        workloadFactory.Created(
            name = "Sample Workload"
          , schedulerParams = TestApp.SampleSchedulerParams(10)
          , processorParams = TestApp.SampleProcessorParams("PrOcEsSeD")
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
      payloads <- scheduler.schedule()
      itemFactory = workload.workItems
      addedItems <- Future.traverse(payloads) {payload => itemFactory(itemFactory.WorkItemAdded(payload))}
    } yield assert(addedItems.size == 10)
  }

  "workload processing" should "process the runnable workload" in {
    val factory = new MongoWorkloadFactory(metadata)

    for {
      workload <- factory.get(DomainObjectGroup.byId(workloadId))
      processor = workload.get.processor
      items <- workload.get.workItems.runnableItemList
      processedItems <- Future.traverse(items) { item =>
        for {
          (itemResult, updatedPayload) <- processor.process(item.payload)
          result <- item(item.FinishedProcessing(updatedPayload, itemResult))
        } yield result
      }
    } yield assert(processedItems.size == 10)
  }
}

