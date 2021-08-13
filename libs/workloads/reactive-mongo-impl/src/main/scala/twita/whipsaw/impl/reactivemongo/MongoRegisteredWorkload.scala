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

/**
 * Provides registry entry functionality for a MongoDB-backed `WorkloadRegistry`.
 */
abstract class MongoWorkloadRegistryEntry(
  implicit mongoContext: MongoContext with WorkloadContext
) extends WorkloadRegistryEntry {

  override def forWorkloadId(id: WorkloadId)(
    implicit executionContext: ExecutionContext
  ): Future[Option[Workload[_, _, _]]] = factory.get(byId(id))

  override def factoryForMetadata[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams] = new MongoWorkloadFactory(md)
}

/**
 * Document format for a `RegisteredWorkload` in a Mongo-based `WorkloadRegistry`
 *
 * @param _id
 * @param factoryType
 * @param schedulingStatus
 * @param processingStatus
 * @param stats Structure that contains the current statistics for this `Workload`.
 * @param appAttrs JSON structure that is used to store application-specific attributes that are attached to a
 *                 `Workload` at the time it is first created.
 */
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

/**
 * Shared information that is needed by both the domain object instance, `MongoRegisteredWorklaod` and the domain
 * object group, `MongoRegisteredWorkloads`.
 */
trait RegisteredWorkloadDescriptor
    extends ObjectDescriptor[EventId, RegisteredWorkload, RegisteredWorkloadDoc] {
  implicit def mongoContext: MongoContext

  override protected lazy val collectionName = "workloads"
  override protected def cons: Either[Empty[WorkloadId], RegisteredWorkloadDoc] => RegisteredWorkload =
    o => new MongoRegisteredWorkload(o)
}

/**
 * Provides the `RegisteredWorkload` implementation for a MongoDB-backed workload system.
 *
 * @param underlying Underlying document (or an Empty instance if the document has been deleted).
 * @param executionContext
 * @param mongoContext
 */
class MongoRegisteredWorkload(
  protected val underlying: Either[Empty[WorkloadId], RegisteredWorkloadDoc]
)(implicit executionContext: ExecutionContext,
  override val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, RegisteredWorkload, RegisteredWorkloadDoc]
  with RegisteredWorkloadDescriptor
  with RegisteredWorkload
{
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

/**
 * Provides the domain object group implementation for a MongoDB-based `RegisteredWorkloads` instance.
 * @param executionContext
 * @param mongoContext
 */
class MongoRegisteredWorkloads()(implicit executionContext: ExecutionContext, val mongoContext: MongoContext)
extends ReactiveMongoDomainObjectGroup[ EventId, RegisteredWorkload, RegisteredWorkloadDoc]
  with RegisteredWorkloadDescriptor
  with RegisteredWorkloads
{
  override protected def listConstraint: JsObject = Json.obj()

  override def list(
    q: DomainObjectGroup.Query
  ): Future[List[RegisteredWorkload]] = q match {
    case attrs: RegisteredWorkloads.byAppAttrs =>
      getListByJsonCrit(Json.obj(attrs.kvs.map { case (k, v) => s"appAttrs.${k}" -> v }: _*))
  }

  override def getRunnable: Future[List[RegisteredWorkload]] =
    getListByJsonCrit(Json.obj("stats.runAt" -> Json.obj("$lt" -> Instant.now)))

  override def apply(
    event: RegisteredWorkloads.Event,
    parent: Option[BaseEvent[EventId]]
  ): Future[RegisteredWorkload] = ???
}