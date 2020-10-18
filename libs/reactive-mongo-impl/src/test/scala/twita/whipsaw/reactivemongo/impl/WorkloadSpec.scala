package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.ItemResult
import twita.whipsaw.api.WorkItemProcessor
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.WorkloadEngine
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads

import scala.concurrent.Future

package test {
  import twita.dominion.impl.reactivemongo.MongoContext
  import scala.concurrent.ExecutionContext

  class SampleMongoWorkloadFactory(implicit executionContext: ExecutionContext, mongoContext: MongoContext)
  extends MongoWorkloads[test.SamplePayload] {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }
}

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val workloadFactory = new test.SampleMongoWorkloadFactory
  var workloadId: WorkloadId = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloadFactory(Workloads.Created(name = "Sample Workload"))
    } yield {
      workloadId = createdWorkload.id
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  //def foo[Payload](factory: MongoWorkloads[_], )
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
    val engine = new WorkloadEngine[test.SamplePayload] {
      override lazy val itemProcessor = new WorkItemProcessor[test.SamplePayload] {
        override def process(payload: test.SamplePayload): Future[(ItemResult, test.SamplePayload)] =
          Future.successful((ItemResult.Done, payload.copy(target = payload.target.toUpperCase())))
      }
    }
    val workloadFactory = new test.SampleMongoWorkloadFactory

    for {
      _ <- workloadFactory.process(workloadId, engine)
      items <- workloadFactory.get(DomainObjectGroup.byId(workloadId)).flatMap {
        case None => Future.failed(new RuntimeException("huh?"))
        case Some(workload) => workload.workItems.list
      }
    } yield assert(items.head.payload.target == "STRING TO BE PROCESSED")
  }
}

