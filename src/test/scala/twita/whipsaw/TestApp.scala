package twita.whipsaw

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import enumeratum._
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.whipsaw.api.registry.RegisteredWorkload
import twita.whipsaw.api.registry.WorkloadRegistry
import twita.whipsaw.api.registry.WorkloadRegistryEntry
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.impl.reactivemongo.MongoWorkloadRegistryEntry

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object TestApp {
  implicit val testAppActorSystem = ActorSystem("TestApp")
  implicit val materializer = Materializer(testAppActorSystem)
  implicit val mongoContext = new DevMongoContextImpl with WorkloadContext
  implicit val executionContext = testAppActorSystem.dispatcher

  case class TestAppAttrs(projectId: Option[Long] = None,
                          orgId: Option[Long] = None)
  object TestAppAttrs { implicit val fmt = Json.format[TestAppAttrs] }

  case class SamplePayload(email: String, target: String, touchedCount: Int = 0)
  object SamplePayload { implicit val fmt = Json.format[SamplePayload] }

  // create a holder for the arguments that are going to the scheduler
  case class SampleSchedulerParams(numItems: Int)
  object SampleSchedulerParams {
    implicit val fmt = Json.format[SampleSchedulerParams]
  }

  // created an alternative just to convince myself that the compiler would be unhappy.
  case class WrongSampleSchedulerParams(numItems: Int)
  object WrongSampleSchedulerParams {
    implicit val fmt = Json.format[WrongSampleSchedulerParams]
  }

  // create a scheduler
  class SampleWorkloadScheduler(p: SampleSchedulerParams)
      extends Scheduler[SamplePayload] {
    override def schedule()(
      implicit ec: ExecutionContext
    ): Future[Source[SamplePayload, NotUsed]] = Future {
      Source.fromIterator(
        () =>
          Range(0, p.numItems).iterator.map { index =>
            SamplePayload(s"bplawler+${index}@gmail.com", s"item #${index}")
        }
      )
    }
  }

  // create a holder for processor parameters
  case class SampleProcessorParams(msgToAppend: String)
  object SampleProcessorParams {
    implicit val fmt = Json.format[SampleProcessorParams]
  }

  // create a processor
  class SampleProcessorWithDelay(p: SampleProcessorParams)
      extends Processor[SamplePayload] {
    override def process(payload: SamplePayload)(
      implicit executionContext: ExecutionContext
    ): Future[(ItemResult, SamplePayload)] = Future {
      //Thread.sleep(100)

      if (Random.nextInt(100) < 0)
        (ItemResult.Retry(new RuntimeException()), payload)
      else
        payload.touchedCount match {
          case 0 =>
            (
              ItemResult.Reschedule(Instant.now.plusMillis(20000)),
              payload.copy(touchedCount = payload.touchedCount + 1)
            )
          case _ =>
            (
              ItemResult.Done,
              payload.copy(
                target = List(payload.target, p.msgToAppend)
                  .mkString(":")
                  .toUpperCase(),
                touchedCount = payload.touchedCount + 1
              )
            )
        }
    }
  }

  sealed trait SampleRegistryEntry extends WorkloadRegistryEntry with EnumEntry

  object SampleRegistryEntry
      extends Enum[SampleRegistryEntry]
      with WorkloadRegistry {
    val values = findValues

    override def apply(rw: RegisteredWorkload)(
      implicit executionContext: ExecutionContext
    ): Future[Workload[_, _, _]] = {
      SampleRegistryEntry.withName(rw.factoryType).forWorkloadId(rw.id)
    }

    override def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](
      md: Metadata[Payload, SParams, PParams]
    )(
      implicit executionContext: ExecutionContext
    ): WorkloadFactory[Payload, SParams, PParams] =
      values.find(_.metadata == md).map(_.factoryForMetadata(md)).get

    case object Sample
        extends MongoWorkloadRegistryEntry
        with SampleRegistryEntry {
      lazy val metadata = Metadata(
        new TestApp.SampleWorkloadScheduler(_: TestApp.SampleSchedulerParams),
        new TestApp.SampleProcessorWithDelay(_: TestApp.SampleProcessorParams),
        Seq("email"),
        entryName
      )
      lazy val factory: WorkloadFactory[_, _, _] = factoryForMetadata(metadata)
    }
  }
}
