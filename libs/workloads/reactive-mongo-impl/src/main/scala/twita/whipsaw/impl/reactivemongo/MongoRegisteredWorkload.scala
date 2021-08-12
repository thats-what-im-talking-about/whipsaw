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

  override def forWorkloadId(id: WorkloadId)(
    implicit executionContext: ExecutionContext
  ): Future[Option[Workload[_, _, _]]] = factory.get(byId(id))

  override def factoryForMetadata[Payload: OFormat,
                                  SParams: OFormat,
                                  PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): Option[WorkloadFactory[Payload, SParams, PParams]] =
    Some(new MongoWorkloadFactory(md))
}

case class RegisteredWorkloadDoc(
  _id: WorkloadId,
  factoryType: String,
  schedulingStatus: SchedulingStatus = SchedulingStatus.Init,
  processingStatus: ProcessingStatus = ProcessingStatus.Init,
  stats: WorkloadStatistics = WorkloadStatistics(),
  appAttrs: JsObject,
) extends BaseDoc[WorkloadId]

object RegisteredWorkloadDoc {
  implicit val fmt = Json.format[RegisteredWorkloadDoc]
}

trait RegisteredWorkloadDescriptor
    extends ObjectDescriptor[EventId, RegisteredWorkload, RegisteredWorkloadDoc] {
  implicit def mongoContext: MongoContext

  override protected lazy val collectionName = "workloads"
  override protected def cons
    : Either[Empty[WorkloadId], RegisteredWorkloadDoc] => RegisteredWorkload = {
    o =>
      new MongoRegisteredWorkload(o)
  }
}

class MongoRegisteredWorkload(
  protected val underlying: Either[Empty[WorkloadId], RegisteredWorkloadDoc]
)(implicit executionContext: ExecutionContext,
  override val mongoContext: MongoContext)
    extends ReactiveMongoObject[
      EventId,
      RegisteredWorkload,
      RegisteredWorkloadDoc
    ]
    with RegisteredWorkloadDescriptor
    with RegisteredWorkload {
  override def stats: WorkloadStatistics = obj.stats
  override def schedulingStatus: SchedulingStatus = obj.schedulingStatus
  override def processingStatus: ProcessingStatus = obj.processingStatus
  override def factoryType: String = obj.factoryType
  override def appAttrs: JsObject = obj.appAttrs
  override def apply(
    event: RegisteredWorkload.Event,
    parent: Option[BaseEvent[EventId]]
  ): Future[RegisteredWorkload] = ???

  def refresh: Future[RegisteredWorkload] = {
    for {
      coll <- objCollectionFt
      one <- coll.find(Json.obj("_id" -> id), None).one[RegisteredWorkloadDoc]
    } yield cons(Right(one.get))
  }
}

class MongoRegisteredWorkloads()(implicit executionContext: ExecutionContext,
                                 val mongoContext: MongoContext)
    extends ReactiveMongoDomainObjectGroup[
      EventId,
      RegisteredWorkload,
      RegisteredWorkloadDoc
    ]
    with RegisteredWorkloadDescriptor
    with RegisteredWorkloads {
  override protected def listConstraint: JsObject = Json.obj()

  override def list(
    q: DomainObjectGroup.Query
  ): Future[List[RegisteredWorkload]] = q match {
    case attrs: RegisteredWorkloads.byAppAttrs =>
      getListByJsonCrit(Json.obj(attrs.kvs.map {
        case (k, v) => s"appAttrs.${k}" -> v
      }: _*))
  }

  override def getRunnable: Future[List[RegisteredWorkload]] =
    getListByJsonCrit(Json.obj("stats.runAt" -> Json.obj("$lt" -> Instant.now)))

  override def apply(
    event: RegisteredWorkloads.Event,
    parent: Option[BaseEvent[EventId]]
  ): Future[RegisteredWorkload] = ???
}
