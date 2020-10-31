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

trait Manager {
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def schedulingStatus: SchedulingStatus
  def processingStatus: ProcessingStatus
  def workers: Workers

  def executeWorkload(): Future[(SchedulingStatus, ProcessingStatus)] = {
    val wl = workload // transforms workload into a val, fixes compiler error
    val scheduledItemsFt = schedulingStatus match {
      case SchedulingStatus.Init => for {
        running <- wl(wl.ScheduleStatusUpdated(SchedulingStatus.Running))
        result <- wl.schedule()
        updated <- wl(wl.ScheduleStatusUpdated(result))
      } yield result
      case _ => Future.successful(SchedulingStatus.Finished)
    }

    val processRunnablesFt = processingStatus match {
      case ProcessingStatus.Finished => Future.successful(ProcessingStatus.Finished)
      case _ => for {
        // TODO: Future.traverse won't work here if there are LOTS of work items.  Need to stream this.
        running <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Running))
        runnableItems <- wl.workItems.runnableItemList
        workerSeq <- Future.traverse(runnableItems) { item => workers.forItem(item) }
        processed <- Future.traverse(workerSeq) { worker => worker.process().map(worker.workItem.id -> _) }
        updated <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Finished))
      } yield ProcessingStatus.Finished
    }

    for {
      sStatus <- scheduledItemsFt
      pStatus <- processRunnablesFt
    } yield (sStatus, pStatus)
  }
}

trait Managers {
  def forWorkload(workload: Workload[_, _, _]): Future[Manager]
}
