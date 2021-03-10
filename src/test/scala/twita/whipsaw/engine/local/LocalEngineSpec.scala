package twita.whipsaw.engine.local

import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorSystem
import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.TestApp
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.impl.engine.localFunctions.LocalDirector
import twita.whipsaw.impl.reactivemongo.MongoRegisteredWorkloads

class LocalEngineSpec extends AsyncFlatSpec with should.Matchers {
  import TestApp.materializer
  implicit val mongoContext = new DevMongoContextImpl with WorkloadContext
  implicit val actorSystem = ActorSystem("LocalEngineSpec")

  val director = new LocalDirector(
    TestApp.SampleRegistryEntry,
    new MongoRegisteredWorkloads()
  )
  val workloadFactory =
    director.registry(TestApp.SampleRegistryEntry.Sample.metadata)
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(
        workloadFactory.Created(
          name = "Sample Workload",
          schedulerParams = TestApp.SampleSchedulerParams(1000),
          processorParams = TestApp.SampleProcessorParams("PrOcEsSeD")
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
      result
        .find(_.id == workloadId)
        .map { rw =>
          assert(rw.schedulingStatus != SchedulingStatus.Init)
          assert(rw.processingStatus != ProcessingStatus.Init)
        }
        .getOrElse(assert(false))
    }
  }

  "workload" should "be in a delayed state" in {
    for {
      workload <- workloadFactory
        .get(DomainObjectGroup.byId(workloadId))
        .map(_.get)
    } yield {
      assert(workload.stats.runAt.isDefined)
    }
  }

  // The test app has a scheduled 15s delay for each work item, which means that after scheduling the
  // work items the job should have a runAt indicating when it's time to wake up.
  "director" should "be able to continue processing after runAt" in {
    for {
      workload <- workloadFactory
        .get(DomainObjectGroup.byId(workloadId))
        .map(_.get)
      _ = workload.stats.runAt
        .map { until =>
          val sleepUntilRunAt = (until.getEpochSecond - Instant.now.getEpochSecond + 5) * 1000
          Thread.sleep(sleepUntilRunAt)
        }
      result <- director.delegateRunnableWorkloads()
    } yield {
      result
        .find(_.id == workloadId)
        // There should be no more delayed work items.
        .map(rw => assert(rw.stats.scheduled == 0))
        .getOrElse(assert(false))
    }
  }
}

class TestMonitor extends Actor {
  override def receive: Receive = {
    case m => println(m)
  }
}
