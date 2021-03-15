package twita.whipsaw.impl.engine.localFunctions

import twita.whipsaw.api.engine.Worker
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.workloads.WorkItem

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LocalWorker(override val workItem: WorkItem[_]) extends Worker

class LocalWorkers extends Workers {
  override def forItem(item: WorkItem[_])(
    implicit ec: ExecutionContext
  ): Future[Worker] = Future.successful(new LocalWorker(item))
}
