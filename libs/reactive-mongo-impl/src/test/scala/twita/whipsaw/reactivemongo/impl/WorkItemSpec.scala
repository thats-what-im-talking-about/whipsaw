package twita.whipsaw.reactivemongo.impl

import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._
import play.api.libs.json.Format
import twita.dominion.api.BaseEvent
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.EventId
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.WorkloadId

import scala.concurrent.Future

package test {
  import play.api.libs.json.Json

  case class SamplePayload(
      email: String
    , touchedCount: Int = 0
  )
  object SamplePayload { implicit val fmt = Json.format[SamplePayload] }
}

class WorkItemSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new DevMongoContextImpl
  val workItems = new MongoWorkItems(new Workload[test.SamplePayload] {
    override def name: String = "Dummy Workload"
    override def id: WorkloadId = WorkloadId("dummy-workload-id")
    override def workItems: WorkItems[test.SamplePayload] = ???
    override def apply(event: Workload.Event, parent: Option[BaseEvent[EventId]]): Future[Workload[test.SamplePayload]] = ???
  }) {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }

  "workItems" should "be created" in {
    for {
      createdItem <- workItems(WorkItems.WorkItemAdded(test.SamplePayload("bplawler@gmail.com")))
    } yield assert(createdItem.payload.email == "bplawler@gmail.com")
  }
}

