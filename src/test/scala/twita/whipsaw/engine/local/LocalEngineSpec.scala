package twita.whipsaw.engine.local

import akka.actor.ActorSystem
import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.TestApp
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.impl.engine.localFunctions.LocalDirector
import twita.whipsaw.impl.reactivemongo.MongoRegisteredWorkloads

class LocalEngineSpec extends AsyncFlatSpec with should.Matchers {
  import TestApp.materializer
  implicit val mongoContext = new DevMongoContextImpl with WorkloadContext
  implicit val actorSystem = ActorSystem("LocalEngineSpec")

  val director = new LocalDirector(TestApp.SampleRegistryEntry, new MongoRegisteredWorkloads())
  val workloadFactory = director.registry(TestApp.SampleRegistryEntry.Sample.metadata)
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(
        workloadFactory.Created(
          name = "Sample Workload"
          , schedulerParams = TestApp.SampleSchedulerParams(10000)
          , processorParams = TestApp.SampleProcessorParams("PrOcEsSeD")
        )
      )
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  "director" should "be able to execute a runnable worklaod" in {
      for {
        result <- director.delegateRunnableWorkloads()
      } yield {
        println(result)
        assert(result.size == 1)
      }
  }
}
