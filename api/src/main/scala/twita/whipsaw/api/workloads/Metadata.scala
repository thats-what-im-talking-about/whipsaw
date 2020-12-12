package twita.whipsaw.api.workloads

/**
  * Workload instances will always be created with an instance of this class which provides the factories that create
  * the correct Scheduler and Processor for this workload.
  * @param scheduler {{RegisteredScheduler}} instance which, given an instance of {{SParams}} will create a new
  *                  {{WorkloadScheduler}} instance for use by this {{Workload}}.
  * @param processor {{RegisteredProcessor}} instance which, given an instance of {{PParams}} will create a new
  *                  {{WorkItemProcessor}} instance for use by this {{Workload}}.
  * @param payloadUniqueConstraint List of fields <b>in the Payload</b> that will be used to create the uniqueness
  *                                constraint for workItems.
  * @tparam Payload
  * @tparam SParams
  * @tparam PParams
  */
case class Metadata[Payload, SParams, PParams](
    scheduler: SParams => Scheduler[Payload]
  , processor: PParams => Processor[Payload]
  , payloadUniqueConstraint: Seq[String]
  , factoryType: String
  , retryPolicy: RetryPolicy = MaxRetriesWithExponentialBackoff(3)
) {
  assert(payloadUniqueConstraint.size > 0,
    "In order for a scheduler to be restartable, you must specify the payload fields used to determine uniqueness.")
}
