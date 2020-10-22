package twita.whipsaw.api

import play.api.libs.json.Format
import play.api.libs.json.OFormat
import twita.whipsaw.api.WorkItems.WorkItemAdded

import scala.concurrent.Future

trait ScheduleCompletion

/**
  * Encapsulates the logic for scheduling a particular Workload.  In this context, the term "scheduling" refers to
  * producing an arbitrarily large number of WorkItems which are to be added to a Workload.  Presumably these items
  * would all also have a `runAt` time attached to them, so that an engine run against this Workload would know to
  * process the item when the time comes.
  *
  * Workload scheduling may in itself be a long-running process.  For example, if the workItems are produced as a
  * result of processing a huge result set, it is the job of the scheduler to issue the query and process that
  * result set.  Because this scheduling process may take a long time, it is also possible that this process may
  * be interrupted before it is completed.  Therefore, it is required that each scheduling task be restartable.
  * Restartability may take one of two different forms.  Either we are able to save checkpoints and somehow restart
  * the scheduling job from where it left off or we are able to restart the job from the beginning and not have any
  * duplicate workItems be scheduled.
  */
trait WorkloadScheduler[Payload] {
  def schedule(payloads: Iterator[Payload]): Future[ScheduleCompletion]
}

trait RegisteredScheduler[SParams, Payload] {
  def withParams(p: SParams): WorkloadScheduler[Payload]
}
