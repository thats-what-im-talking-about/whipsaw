package twita.whipsaw.api.workloads

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Defines a policy to be used when the processing of a `WorkItem` results in an `ItemResult` of `Retry`.  This
  * method is invoked by a `Worker` instance when a `Retry` result is encountered; the application is never expected
  * to make a call to this method.  The `RetryPolicy` is one of the parameters passed into the `Metadata` definition
  * for a particular workload type, and it allows the application to provide variable logic to use when retries are
  * encountered.
  */
trait RetryPolicy {

  /**
    * Called when a WorkItem is processed and returns a `Reschedule` result.
    * @param result `ItemResult` instance that resulted in this retry.
    * @param item `WorkItem` instance whose processing resulted in this retry.
    * @param executionContext
    * @tparam Payload
    * @return a `Future` that will be completed by a tuple containing the new `ItemResult` and the updated Payload
    *         that happened as a result of processing this `RetryPolicy`.
    */
  def retry[Payload](result: ItemResult, item: WorkItem[Payload])(
    implicit executionContext: ExecutionContext
  ): Future[(ItemResult, WorkItem[Payload])]
}

/**
  * `RetryPolicy` implementation that schedules a retry with exponential backoff.
  * @param maxRetries The maximum number of retries to process before giving up.  Once `maxRetries` has been reached
  *                   the WorkItem will be errored out and not retried again.
  */
case class MaxRetriesWithExponentialBackoff(maxRetries: Int)
    extends RetryPolicy {

  override def retry[Payload](result: ItemResult, item: WorkItem[Payload])(
    implicit ec: ExecutionContext
  ): Future[(ItemResult, WorkItem[Payload])] = {
    if (item.retryCount < maxRetries)
      item(
        item.RetryScheduled(
          at = Instant.now.plusSeconds(2 ^ item.retryCount * 60),
          tryNumber = item.retryCount
        )
      ).map(result -> _)
    else
      item(item.MaxRetriesReached()).map(
        ItemResult.Error(
          new RuntimeException(s"max retries reached: ${item.retryCount}")
        ) -> _
      )
  }
}
