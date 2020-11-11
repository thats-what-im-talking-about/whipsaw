package twita.whipsaw.impl.engine.localFunctions

import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.engine.Managers
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.workloads.Workload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LocalManager(
  override val workload: Workload[_, _, _]
)(implicit val executionContext: ExecutionContext) extends Manager {
  override def workers: Workers = new LocalWorkers
}

class LocalManagers()(implicit executionContext: ExecutionContext) extends Managers {
  override def forWorkload(workload: Workload[_, _, _]): Future[Manager] = Future.successful(new LocalManager(workload))
}