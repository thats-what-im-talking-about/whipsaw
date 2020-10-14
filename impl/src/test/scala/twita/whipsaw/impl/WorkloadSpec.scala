package twita.whipsaw.impl

import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.MongoContextImpl
import twita.whipsaw.api.Workloads

class WorkloadSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new MongoContextImpl
  val workloads = new MongoWorkloads {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }

  "workloads" should "be created" in {
    for {
      createdWorkload <- workloads(Workloads.Created(name = "Sample Workload"))
    } yield assert(createdWorkload.name == "Sample Workload")
  }
}

