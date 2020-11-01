package twita.whipsaw.api.engine

import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Manager {
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def workers: Workers

  def executeWorkload(): Future[(SchedulingStatus, ProcessingStatus)] = {
    val wl = workload // transforms workload into a val, fixes compiler error
    val scheduledItemsFt = workload.schedulingStatus match {
      case SchedulingStatus.Init => for {
        running <- wl(wl.ScheduleStatusUpdated(SchedulingStatus.Running))
        result <- wl.schedule()
        updated <- wl(wl.ScheduleStatusUpdated(result))
      } yield result
      case _ => Future.successful(SchedulingStatus.Completed)
    }

    val processRunnablesFt = workload.processingStatus match {
      case ProcessingStatus.Completed => Future.successful(ProcessingStatus.Completed)
      case _ => for {
        // TODO: Future.traverse won't work here if there are LOTS of work items.  Need to stream this.
        running <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Running))
        runnableItems <- wl.workItems.runnableItemList
        workerSeq <- Future.traverse(runnableItems) { item => workers.forItem(item) }
        processed <- Future.traverse(workerSeq) { worker => worker.process().map(worker.workItem.id -> _) }
        updated <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Completed))
      } yield ProcessingStatus.Completed
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
