package twita.whipsaw.engine.local

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup.byId
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.TestApp
import twita.whipsaw.api.engine.ForWorkload
import twita.whipsaw.api.engine.Monitor
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
          , schedulerParams = TestApp.SampleSchedulerParams(10)
          , processorParams = TestApp.SampleProcessorParams("PrOcEsSeD")
        )
      )
      monitor = new Monitor(director, actorSystem.actorOf(Props[TestMonitor], "testMonitor"))
      _ <- monitor.addFilter(ForWorkload(createdWorkload.id ))
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  "director" should "be able to execute a runnable worklaod" in {
      for {
        rwOpt <- director.registeredWorkloads.get(byId(workloadId))
        _ = rwOpt.map(rw => println(s"Stats from LocalEngineSpec: ${rw.stats}"))
        result <- director.delegateRunnableWorkloads()
      } yield {
        Thread.sleep(500000)
        assert(result.size == 1)
      }
  }
}

class TestMonitor extends Actor {
  override def receive: Receive = {
    case m => println(m)
  }
}
