package twita.whipsaw.impl.engine.localFunctions

import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.engine.Managers
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.engine.WorkloadMonitor
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LocalManager(
  override val workload: Workload[_, _, _]
)(implicit val executionContext: ExecutionContext) extends Manager {
  override def workers: Workers = new LocalWorkers

  /**
    * Interface that allows external observers to subscribe to all of the events that are applied to this Workload.
    *
    * @param monitor Object to which events in this Workload will be sent.
    */
  override def watch(monitor: WorkloadMonitor): Unit = ???
}

class LocalManagers()(implicit executionContext: ExecutionContext) extends Managers {
  override def forWorkloadId(workloadId: WorkloadId): Future[Manager] = ???

  override def forWorkload(workload: Workload[_, _, _]): Future[Manager] = Future.successful(new LocalManager(workload))
}