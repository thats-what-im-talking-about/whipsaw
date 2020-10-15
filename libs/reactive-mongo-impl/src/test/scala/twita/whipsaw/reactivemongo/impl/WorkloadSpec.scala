package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.Workloads

package test {
  import twita.dominion.impl.reactivemongo.MongoContext
  import scala.concurrent.ExecutionContext

  class SampleMongoWorkloads(implicit executionContext: ExecutionContext, mongoContext: MongoContext) extends MongoWorkloads[test.SamplePayload]
}

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val workloads = new test.SampleMongoWorkloads {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }
  var workload: Workload[test.SamplePayload] = _

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloads(Workloads.Created(name = "Sample Workload"))
    } yield {
      workload = createdWorkload
      assert(createdWorkload.name == "Sample Workload")
    }
  }

  "work items" should "be added to workloads via apply()" in {
    for {
      createdItem <- workload.workItems.apply(
        WorkItems.WorkItemAdded(test.SamplePayload("bplawler@gmail.com"))
      )
    } yield assert(createdItem.payload.email == "bplawler@gmail.com")
  }
}

