package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.TestApp
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.Future

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val metadata = Metadata(
      new TestApp.SampleWorkloadScheduler(_: TestApp.SampleSchedulerParams)
    , new TestApp.SampleWorkItemProcessor(_: TestApp.SampleProcessorParams)
    , Seq("email")
    , "Sample"
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
}

