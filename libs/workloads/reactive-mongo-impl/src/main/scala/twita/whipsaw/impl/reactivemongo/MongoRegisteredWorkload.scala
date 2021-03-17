package twita.whipsaw.impl.reactivemongo

import java.time.Instant

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import reactivemongo.play.json.compat._
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.byId
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.whipsaw.api.registry.RegisteredWorkload
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.registry.WorkloadRegistryEntry
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatistics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

abstract class MongoWorkloadRegistryEntry(
  implicit mongoContext: MongoContext with WorkloadContext
) {
  self: WorkloadRegistryEntry =>

  override def forWorkloadId(
    id: WorkloadId
  )(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]] =
    factory.get(byId(id)).map {
      case Some(w) => w
      case None    => throw new RuntimeException("no can do")
    }

  override def factoryForMetadata[Payload: OFormat,
                                  SParams: OFormat,
                                  PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams] = new MongoWorkloadFactory(md)
}

case class RegisteredWorkloadDoc[Attr: OFormat](
  _id: WorkloadId,
  attr: Attr,
  factoryType: String,
  schedulingStatus: SchedulingStatus = SchedulingStatus.Init,
  processingStatus: ProcessingStatus = ProcessingStatus.Init,
  stats: WorkloadStatistics = WorkloadStatistics()
) extends BaseDoc[WorkloadId]

object RegisteredWorkloadDoc {
  implicit def fmt[Attr: OFormat] = Json.format[RegisteredWorkloadDoc[Attr]]
}

trait RegisteredWorkloadDescriptor[Attr]
    extends ObjectDescriptor[EventId, RegisteredWorkload[Attr], RegisteredWorkloadDoc[
      Attr
    ]] {
  implicit def mongoContext: MongoContext
  implicit protected def attrFmt: OFormat[Attr]

  override protected lazy val collectionName = "workloads"
  override protected def cons: Either[Empty[WorkloadId], RegisteredWorkloadDoc[
    Attr
  ]] => RegisteredWorkload[Attr] = { o =>
    new MongoRegisteredWorkload(o)
  }
}

class MongoRegisteredWorkload[Attr: OFormat](
  protected val underlying: Either[Empty[WorkloadId], RegisteredWorkloadDoc[
    Attr
  ]]
)(implicit executionContext: ExecutionContext,
  override val mongoContext: MongoContext)
    extends ReactiveMongoObject[EventId, RegisteredWorkload[Attr], RegisteredWorkloadDoc[
      Attr
    ]]
    with RegisteredWorkloadDescriptor[Attr]
    with RegisteredWorkload[Attr] {
  override val attrFmt = implicitly[OFormat[Attr]]
  override def stats: WorkloadStatistics = obj.stats
  override def schedulingStatus: SchedulingStatus = obj.schedulingStatus
  override def processingStatus: ProcessingStatus = obj.processingStatus
  override def factoryType: String = obj.factoryType
  override def attr = obj.attr
  override def apply(
    event: RegisteredWorkload.Event,
    parent: Option[BaseEvent[EventId]]
  ): Future[RegisteredWorkload[Attr]] = ???

  def refresh: Future[RegisteredWorkload[Attr]] = {
    for {
      coll <- objCollectionFt
      one <- coll
        .find(Json.obj("_id" -> id), None)(jsObjectWrites, jsObjectWrites)
        .one[RegisteredWorkloadDoc[Attr]]
    } yield cons(Right(one.get))
  }
}

class MongoRegisteredWorkloads[Attr: OFormat]()(
  implicit executionContext: ExecutionContext,
  val mongoContext: MongoContext
) extends ReactiveMongoDomainObjectGroup[EventId, RegisteredWorkload[Attr], RegisteredWorkloadDoc[
      Attr
    ]]
    with RegisteredWorkloadDescriptor[Attr]
    with RegisteredWorkloads[Attr] {
  override val attrFmt = implicitly[OFormat[Attr]]
  override protected def listConstraint: JsObject = Json.obj()

  override def list(
    q: DomainObjectGroup.Query
  ): Future[List[RegisteredWorkload[Attr]]] = ???

  override def getRunnable: Future[List[RegisteredWorkload[Attr]]] =
    getListByJsonCrit(Json.obj("stats.runAt" -> Json.obj("$lt" -> Instant.now)))

  override def apply(
    event: RegisteredWorkloads.Event,
    parent: Option[BaseEvent[EventId]]
  ): Future[RegisteredWorkload[Attr]] = ???
}
