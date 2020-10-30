package twita.whipsaw.api.engine

import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.WorkItem

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Worker {
  implicit def executionContext: ExecutionContext
  def workItem: WorkItem[_]
  def process(): Future[ItemResult] = workItem.process()
}

trait Workers {
  def forItem(item: WorkItem[_]): Future[Worker]
}
