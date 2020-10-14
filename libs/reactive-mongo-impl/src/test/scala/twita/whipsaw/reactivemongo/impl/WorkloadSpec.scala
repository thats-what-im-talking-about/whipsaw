package twita.whipsaw.reactivemongo.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.Workloads

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val workloads = new MongoWorkloads {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }
  var workload: Workload = _

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
      createdItem <- workload.workItems[test.SampleWorkItemDesc].apply(
        WorkItems.WorkItemAdded(test.SampleWorkItemDesc("bplawler@gmail.com"))
      )
    } yield assert(createdItem.description.email == "bplawler@gmail.com")
  }
}

