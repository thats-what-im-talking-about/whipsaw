package twita.whipsaw.api.engine

import java.time.Instant

import enumeratum._
import play.api.libs.json.Json
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.EventIdGenerator
import twita.whipsaw.api.workloads.Workload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

sealed trait SchedulingStatus extends EnumEntry
object SchedulingStatus extends Enum[SchedulingStatus] with PlayJsonEnum[SchedulingStatus] {
  val values = findValues

  case object Init extends SchedulingStatus
  case object Running extends SchedulingStatus
  case class Completed(created: Int, dups: Int) extends SchedulingStatus
  case object Finished extends SchedulingStatus
}

sealed trait ProcessingStatus extends EnumEntry
object ProcessingStatus extends Enum[ProcessingStatus] with PlayJsonEnum[ProcessingStatus] {
  val values = findValues

  case object Init extends ProcessingStatus
  case object Running extends ProcessingStatus
  case object Waiting extends ProcessingStatus
  case object Finished extends ProcessingStatus
}

trait Manager extends DomainObject[EventId, Manager] {
  override type AllowedEvent = Manager.Event
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def schedulingStatus: SchedulingStatus
  def processingStatus: ProcessingStatus

  def executeWorkload(): Future[(SchedulingStatus, ProcessingStatus)] = {
    val scheduledItemsFt = schedulingStatus match {
      case SchedulingStatus.Init => for {
        running <- apply(Manager.ScheduleStatusUpdated(SchedulingStatus.Running))
        result <- workload.schedule()
        updated <- apply(Manager.ScheduleStatusUpdated(result))
      } yield result
      case _ => Future.successful(SchedulingStatus.Finished)
    }

    val processRunnablesFt = processingStatus match {
      case ProcessingStatus.Finished => Future.successful(ProcessingStatus.Finished)
      case _ => for {
        running <- apply(Manager.ProcessingStatusUpdated(ProcessingStatus.Running))
        result <- workload.process()
        updated <- apply(Manager.ProcessingStatusUpdated(result))
      } yield result
    }

    for {
      sStatus <- scheduledItemsFt
      pStatus <- processRunnablesFt
    } yield (sStatus, pStatus)
  }
}

object Manager {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class ScheduleStatusUpdated(status: SchedulingStatus) extends Event
  object ScheduleStatusUpdated { implicit val fmt = Json.format[ScheduleStatusUpdated] }

  case class ProcessingStatusUpdated(status: ProcessingStatus) extends Event
  object ProcessingStatusUpdated { implicit val fmt = Json.format[ProcessingStatusUpdated] }
}

trait Managers extends DomainObjectGroup[EventId, Manager] {
  def forWorkload(managedWorkload: ManagedWorkload): Future[Manager]
}

trait ManagedWorkload extends DomainObject[EventId, ManagedWorkload]

trait ManagedWorkloads {
  def getRunnable(runAt: Instant = Instant.now): Future[Seq[ManagedWorkload]]
}
