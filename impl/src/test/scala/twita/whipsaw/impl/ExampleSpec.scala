package twita.whipsaw.impl

import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.impl.reactivemongo.MongoContextImpl
import twita.whipsaw.api.WorkItems
import twita.whipsaw.impl.api.SampleWorkItemDesc

package api {

  import play.api.libs.json.Json

  case class SampleWorkItemDesc(
      email: String
    , touchedCount: Int = 0
  )
  object SampleWorkItemDesc { implicit val fmt = Json.format[SampleWorkItemDesc] }
}

class ExampleSpec extends AsyncFlatSpec with should.Matchers {
  implicit val mongoContext = new MongoContextImpl
  val workItems = new MongoWorkItems[api.SampleWorkItemDesc] {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }

  "workItems" should "be created" in {
    for {
      createdItem <- workItems(WorkItems.Created(SampleWorkItemDesc("bplawler@gmail.com")))
    } yield assert(createdItem.description.email == "bplawler@gmail.com")
  }
}

