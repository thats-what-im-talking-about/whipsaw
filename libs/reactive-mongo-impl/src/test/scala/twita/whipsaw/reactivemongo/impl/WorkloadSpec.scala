package twita.whipsaw.reactivemongo.impl

import enumeratum._
import org.scalatest.flatspec._
import org.scalatest.matchers._
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads

import scala.concurrent.Future

package test {
  import twita.dominion.api.DomainObjectGroup
  import twita.dominion.impl.reactivemongo.MongoContext

  import scala.concurrent.ExecutionContext

  object WorkloadRegistry extends Enum[WorkloadRegistryEntry] {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit lazy val mongoContext = new DevMongoContextImpl
    val values = findValues

    case object Sample extends WorkloadRegistryEntry(new SampleMongoWorkloadFactory())

    private lazy val workloadFactory = new MongoWorkloads[String]
    def byId(id: WorkloadId): Future[Option[Workloads[_]]] = workloadFactory.get(DomainObjectGroup.byId(id)).map { wlOpt =>
      wlOpt.map(wl => withName(wl.factoryType).entry)
    }
    def byFactoryType(factoryType: String): Workloads[_] = withName(factoryType).entry
  }

  class SampleMongoWorkloadFactory(implicit executionContext: ExecutionContext, mongoContext: MongoContext)
  extends MongoWorkloads[test.SamplePayload] {
    override def eventLogger: EventLogger = new MongoObjectEventStackLogger(4)
  }

  sealed class WorkloadRegistryEntry(val entry: Workloads[_]) extends EnumEntry {
    override def entryName: String = entry.getClass.getName
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
    println(test.WorkloadRegistry.values)
    val factory = test.WorkloadRegistry.withName("twita.whipsaw.reactivemongo.impl.test.SampleMongoWorkloadFactory")
    for {
      workload <- factory.entry.get(DomainObjectGroup.byId(workloadId))
      createdItem <- workload.get.workItems.apply(
        WorkItems.WorkItemAdded(test.SamplePayload("bplawler@gmail.com"))
      )
    } yield assert(createdItem.id != null)
  }

  "workloads" should "be generically retrievable from the database" in {
    for {
      workloadFactory <- test.WorkloadRegistry.byId(workloadId)
    } yield assert(1 == 1)
  }
}

