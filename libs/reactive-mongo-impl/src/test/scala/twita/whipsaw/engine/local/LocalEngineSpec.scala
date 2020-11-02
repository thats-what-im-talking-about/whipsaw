package twita.whipsaw.engine.local

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.TestApp
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.reactivemongo.impl.MongoRegisteredWorkloads
import twita.whipsaw.reactivemongo.impl.MongoWorkloadFactory

class LocalEngineSpec extends AsyncFlatSpec with should.Matchers {
  import TestApp.materializer
  implicit val mongoContext = new DevMongoContextImpl
  val director = new LocalDirector(TestApp.SampleWorkloadRegistry, new MongoRegisteredWorkloads())
  val workloadFactory = new MongoWorkloadFactory(TestApp.sampleMetadata)
  var workloadId: WorkloadId = _

  val workloadFt = for {
    createdWorkload <- workloadFactory(
      workloadFactory.Created(
          name = "Sample Workload"
        , schedulerParams = TestApp.SampleSchedulerParams(100000)
        , processorParams = TestApp.SampleProcessorParams("PrOcEsSeD")
      )
    )
  } yield {
    workloadId = createdWorkload.id
    assert(createdWorkload.name == "Sample Workload")
  }

  "director" should "be able to execute a runnable worklaod" in {
    for {
      _ <- workloadFt
      result <- director.delegateRunnableWorkloads()
    } yield {
      println(result)
      assert(result.size == 1)
    }
  }
}
