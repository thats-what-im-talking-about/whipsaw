package twita.whipsaw.api

import scala.concurrent.ExecutionContext

trait WorkloadEngine[Payload] {
  def itemProcessor: WorkItemProcessor[Payload]

  def process(workload: Workload[Payload])(implicit executionContext: ExecutionContext) = {
    workload.workItems.runnableItemList.map { items =>
      items.map { item =>
        itemProcessor.process(item.payload).map { case (result, newPayload) =>
          item(WorkItem.Processed(newPayload, result))
        }
      }
    }
  }
}
