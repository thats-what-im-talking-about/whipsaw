package twita.whipsaw.api.workloads

import java.time.Instant

import scala.concurrent.Future

trait RetryPolicy {
  def retry[Payload](item: WorkItem[Payload]): Future[WorkItem[Payload]]
}

case class MaxRetriesWithExponentialBackoff(maxRetries: Int) extends RetryPolicy {
  override def retry[Payload](item: WorkItem[Payload]): Future[WorkItem[Payload]] = {
    if(item.retryCount < maxRetries)
      item(item.RetryScheduled(at = Instant.now.plusSeconds(2^item.retryCount * 60), tryNumber = item.retryCount))
    else
      item(item.MaxRetriesReached())
  }
}



