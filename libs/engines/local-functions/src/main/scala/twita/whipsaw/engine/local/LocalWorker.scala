package twita.whipsaw.engine.local

import twita.whipsaw.api.engine.Worker
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.workloads.WorkItem

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LocalWorker(override val workItem: WorkItem[_])(
  implicit val executionContext: ExecutionContext
) extends Worker {
}

class LocalWorkers()(implicit executionContext: ExecutionContext) extends Workers {
  override def forItem(item: WorkItem[_]): Future[Worker] = Future.successful(new LocalWorker(item))
}
