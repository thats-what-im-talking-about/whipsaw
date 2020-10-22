package twita.whipsaw.reactivemongo.impl

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Writes
import reactivemongo.play.json.collection.JSONCollection
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.whipsaw.api.EventId
import twita.whipsaw.api.RegisteredProcessor
import twita.whipsaw.api.RegisteredScheduler
import twita.whipsaw.api.WorkItemProcessor
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.WorkloadScheduler
import twita.whipsaw.api.Workloads

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadDoc[
    SParams: OFormat
  , RS <: RegisteredScheduler[SParams, _]: Format
  , PParams: OFormat
  , RP <: RegisteredProcessor[PParams, _]: Format
](
    _id: WorkloadId
  , name: String
  , scheduler: RS
  , schedulerParams: SParams
  , processor: RP
  , processorParams: PParams
) extends BaseDoc[WorkloadId]

object WorkloadDoc {
  implicit def fmt [
      SParams: OFormat
    , RS <: RegisteredScheduler[SParams, _]: Format
    , PParams: OFormat
    , RP <: RegisteredProcessor[PParams, _]: Format
  ] = Json.format[WorkloadDoc[SParams, RS, PParams, RP]]
}

trait WorkloadDescriptor[
    Payload
  , SParams
  , RS <: RegisteredScheduler[SParams, Payload]
  , PParams
  , RP <: RegisteredProcessor[PParams, Payload]]
extends ObjectDescriptor[EventId, Workload[Payload, SParams, RS, PParams, RP], WorkloadDoc[SParams, RS, PParams, RP]]
{
  implicit def mongoContext: MongoContext
  implicit def payloadFmt: OFormat[Payload]
  implicit def sParamsFmt: OFormat[SParams]
  implicit def schedulerFmt: Format[RS]
  implicit def pParamsFmt: OFormat[PParams]
  implicit def processorFmt: Format[RP]

  override protected def objCollectionFt: Future[JSONCollection] = mongoContext.getCollection("workloads")
  override protected def cons: Either[Empty[WorkloadId], WorkloadDoc[SParams, RS, PParams, RP]] => Workload[Payload, SParams, RS, PParams, RP] = {
    o => new MongoWorkload[Payload, SParams, RS, PParams, RP](o)
  }
}

class MongoWorkload[
    Payload
  , SParams
  , RS <: RegisteredScheduler[SParams, Payload]
  , PParams
  , RP <: RegisteredProcessor[PParams, Payload]
](protected val underlying: Either[Empty[WorkloadId], WorkloadDoc[SParams, RS, PParams, RP]]
)(implicit executionContext: ExecutionContext
         , override val mongoContext: MongoContext
         , override val payloadFmt: OFormat[Payload]
         , override val sParamsFmt: OFormat[SParams]
         , override val schedulerFmt: Format[RS]
         , override val pParamsFmt: OFormat[PParams]
         , override val processorFmt: Format[RP])
extends ReactiveMongoObject[EventId, Workload[Payload, SParams, RS, PParams, RP], WorkloadDoc[SParams, RS, PParams, RP]]
  with WorkloadDescriptor[Payload, SParams, RS, PParams, RP]
  with Workload[Payload, SParams, RS, PParams, RP]
{
  override def name: String = obj.name

  override def workItems: WorkItems[Payload] = new MongoWorkItems[Payload](this)

  override def scheduler: WorkloadScheduler[Payload] = obj.scheduler.withParams(obj.schedulerParams)

  override def processor: WorkItemProcessor[Payload] = obj.processor.withParams(obj.processorParams)

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload, SParams, RS, PParams, RP]] = ???
}

class MongoWorkloads[
    Payload
  , SParams
  , RS <: RegisteredScheduler[SParams, Payload]
  , PParams
  , RP <: RegisteredProcessor[PParams, Payload]
](
  implicit executionContext: ExecutionContext
         , val mongoContext: MongoContext
         , override val payloadFmt: OFormat[Payload]
         , override val sParamsFmt: OFormat[SParams]
         , override val schedulerFmt: Format[RS]
         , override val pParamsFmt: OFormat[PParams]
         , override val processorFmt: Format[RP]
)
extends ReactiveMongoDomainObjectGroup[EventId, Workload[Payload, SParams, RS, PParams, RP], WorkloadDoc[SParams, RS, PParams, RP]]
  with WorkloadDescriptor[Payload, SParams, RS, PParams, RP]
  with Workloads[Payload, SParams, RS, PParams, RP]
{
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[Workload[Payload, SParams, RS, PParams, RP]]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload, SParams, RS, PParams, RP]] = event match {
    case evt: Workloads.Created[SParams, RS, PParams, RP] => create(
      WorkloadDoc[SParams, RS, PParams, RP](
          _id = WorkloadId()
        , name = evt.name
        , scheduler = evt.scheduler
        , schedulerParams = evt.schedulerParams
        , processor = evt.processor
        , processorParams = evt.processorParams
      ), evt, parent)(evt.fmt)
    }

}
