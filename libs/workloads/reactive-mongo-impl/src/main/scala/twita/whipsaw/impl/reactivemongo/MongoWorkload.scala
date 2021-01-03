package twita.whipsaw.impl.reactivemongo

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.dominion.impl.reactivemongo.ReactiveMongoObject.SetOp
import twita.whipsaw.api.engine.WorkloadStatistics
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId
import reactivemongo.play.json.compat._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadDoc[SParams: OFormat, PParams: OFormat] (
    _id: WorkloadId
  , name: String
  , factoryType: String
  , schedulerParams: SParams
  , processorParams: PParams
  , schedulingStatus: SchedulingStatus = SchedulingStatus.Init
  , processingStatus: ProcessingStatus = ProcessingStatus.Init
  , stats: WorkloadStatistics = WorkloadStatistics()
) extends BaseDoc[WorkloadId]

object WorkloadDoc {
  implicit def fmt[SParams: OFormat, PParams: OFormat] = Json.format[WorkloadDoc[SParams, PParams]]
}

trait WorkloadDescriptor[Payload, SParams, PParams]
extends ObjectDescriptor[EventId, Workload[Payload, SParams, PParams], WorkloadDoc[SParams, PParams]]
{
  implicit def mongoContext: MongoContext with WorkloadContext
  implicit def pFmt: OFormat[Payload]
  implicit def spFmt: OFormat[SParams]
  implicit def ppFmt: OFormat[PParams]
  implicit val dFmt: OFormat[WorkloadDoc[SParams, PParams]] = WorkloadDoc.fmt[SParams, PParams]
  def metadata: Metadata[Payload, SParams, PParams]
  override lazy val eventLogger = new MongoObjectEventStackLogger(50)

  override protected lazy val collectionName = "workloads"
  override protected def cons: Either[Empty[WorkloadId], WorkloadDoc[SParams, PParams]] => Workload[Payload, SParams, PParams] = {
    o => new MongoWorkload[Payload, SParams, PParams](metadata, o)
  }
}

class MongoWorkload[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    val metadata: Metadata[Payload, SParams, PParams]
  , protected val underlying: Either[Empty[WorkloadId], WorkloadDoc[SParams, PParams]]
)(implicit executionContext: ExecutionContext, override val mongoContext: MongoContext with WorkloadContext)
extends ReactiveMongoObject[EventId, Workload[Payload, SParams, PParams], WorkloadDoc[SParams, PParams]]
  with WorkloadDescriptor[Payload, SParams, PParams]
  with Workload[Payload, SParams, PParams]
{
  override val pFmt = implicitly[OFormat[Payload]]
  override val spFmt = implicitly[OFormat[SParams]]
  override val ppFmt = implicitly[OFormat[PParams]]
  override def name: String = obj.name

  override def workItems: WorkItems[Payload] = new MongoWorkItems[Payload](this)

  override def scheduler: Scheduler[Payload] = metadata.scheduler(obj.schedulerParams)

  override def processor: Processor[Payload] = metadata.processor(obj.processorParams)

  override def schedulingStatus: SchedulingStatus = obj.schedulingStatus

  override def processingStatus: ProcessingStatus = obj.processingStatus

  override def stats: Future[WorkloadStatistics] = Future.successful(obj.stats)

  override def stats_=(workloadStatistics: WorkloadStatistics): Future[WorkloadStatistics] =
    for {
      objColl <- objCollectionFt
      updateResult <- objColl.update(ordered=false).one(
          Json.obj("_id" -> Json.toJson(id))
        , Json.obj("$set" -> Json.obj("stats" -> Json.toJson(workloadStatistics)))
      )
    } yield workloadStatistics

  override def apply(
      event: AllowedEvent
    , parent: Option[BaseEvent[EventId]]
  ) : Future[Workload[Payload, SParams, PParams]] = event match {
    case evt: ScheduleStatusUpdated => update(SetOp(Json.toJsObject(evt)), evt, parent)
    case evt: ProcessingStatusUpdated => update(SetOp(Json.toJsObject(evt)), evt, parent)
  }
}

class MongoWorkloadFactory[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    val metadata: Metadata[Payload, SParams, PParams]
)(
    implicit executionContext: ExecutionContext
  , val mongoContext: MongoContext with WorkloadContext
)
extends ReactiveMongoDomainObjectGroup[EventId, Workload[Payload, SParams, PParams], WorkloadDoc[SParams, PParams]]
  with WorkloadDescriptor[Payload, SParams, PParams]
  with WorkloadFactory[Payload, SParams, PParams]
{
  override val pFmt = implicitly[OFormat[Payload]]
  override val spFmt = implicitly[OFormat[SParams]]
  override val ppFmt = implicitly[OFormat[PParams]]
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[Workload[Payload, SParams, PParams]]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload, SParams, PParams]] = event match {
    case evt: Created => create(
      WorkloadDoc(
          _id = WorkloadId()
        , name = evt.name
        , factoryType = metadata.factoryType
        , schedulerParams = evt.schedulerParams
        , processorParams = evt.processorParams
      ), evt, parent)
    }
}
