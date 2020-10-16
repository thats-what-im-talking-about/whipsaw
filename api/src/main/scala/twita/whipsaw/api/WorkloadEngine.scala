package twita.whipsaw.api

trait WorkloadEngine {
  def process[Payload](workload: Workload[Payload], itemProcessor: WorkItemProcessor[Payload])
}
