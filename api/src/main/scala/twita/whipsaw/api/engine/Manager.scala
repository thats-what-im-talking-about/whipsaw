package twita.whipsaw.api.engine

import java.time.Instant

import akka.stream.Materializer
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Manager {
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def workers: Workers

  def executeWorkload()(implicit m: Materializer): Future[(SchedulingStatus, ProcessingStatus)] = {
    val wl = workload // transforms workload into a val, fixes compiler error

    val scheduledItemsFt = workload.schedulingStatus match {
      case SchedulingStatus.Completed => Future.successful(SchedulingStatus.Completed)
      case _ => for {
        running <- wl(wl.ScheduleStatusUpdated(SchedulingStatus.Running))
        result <- wl.schedule()
        updated <- wl(wl.ScheduleStatusUpdated(result))
      } yield result
    }

    def processRunnablesFt(last: Boolean): Future[ProcessingStatus] = workload.processingStatus match {
      case ProcessingStatus.Completed => Future.successful(ProcessingStatus.Completed)
      case _ =>
        (for {
          running <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Running))
          runnableItems <- wl.workItems.runnableItemSource(Instant.now, workload.batchSize)
          result <- runnableItems
            .mapAsyncUnordered(workload.desiredNumWorkers)(workers.forItem(_).flatMap(_.process()))
            .runFold(0) { case (result, _) => result + 1 }
          _ <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Completed))
        } yield result).flatMap { numProcessed =>
          println(s"processed ${numProcessed} entries")
          Thread.sleep(5)
          numProcessed match {
            case 0 if last => Future.successful(ProcessingStatus.Completed)
            case 0 => processRunnablesFt(true)
            case n => processRunnablesFt(false)
          }
        }
    }

    val processResult = processRunnablesFt(false)

    for {
      sStatus <- scheduledItemsFt
      pStatus <- processResult
    } yield (sStatus, pStatus)
  }
}

trait Managers {
  def forWorkload(workload: Workload[_, _, _]): Future[Manager]
}
