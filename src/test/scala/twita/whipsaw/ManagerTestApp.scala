package twita.whipsaw

import java.time.Instant

import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.engine.Worker
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.engine.WorkloadExecutionGraph
import twita.whipsaw.api.workloads
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkItem
import twita.whipsaw.api.workloads.WorkItemId
import twita.whipsaw.api.workloads.WorkItemStatus
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatistics
import twita.whipsaw.monitor.WorkloadStatsTracker

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

case class P(i: Int, s: String)
object P { implicit val fmt = Json.format[P] }
class TestWorkItem(nid: Int) extends WorkItem[P] {
  override implicit def pFmt: OFormat[P] = P.fmt
  override def runAt: Option[Instant] = ???
  override def retryCount: Int = 0
  override def payload: P = ???
  override def status: Option[WorkItemStatus] = ???
  override def _eventStack: Option[Seq[JsObject]] = ???
  override protected def workload: Workload[P, _, _] = ???
  override def id: WorkItemId = WorkItemId(nid.toString)
  override def process()(implicit ec: ExecutionContext): Future[ItemResult] =
    Future.successful(ItemResult.Done)

  override def apply(
    event: Event,
    parent: Option[BaseEvent[workloads.EventId]]
  ): Future[WorkItem[P]] = ???
}

class TestWorkload(nid: Int) extends Workload[P, P, P] {
  override def name: String = ???
  override def workItems: WorkItems[P] = ???
  override def scheduler: Scheduler[P] = ???
  override def processor: Processor[P] = ???
  override def metadata: Metadata[P, P, P] = ???
  override def schedulingStatus: SchedulingStatus = ???
  override def processingStatus: ProcessingStatus = ???
  override def stats: WorkloadStatistics = ???
  override lazy val id: WorkloadId = WorkloadId(nid.toString)
  override def apply(
    event: Event,
    parent: Option[BaseEvent[workloads.EventId]]
  ): Future[Workload[P, P, P]] = Future.successful(this)
}

class TestWorker(val workItem: WorkItem[_]) extends Worker

class TestManager(val actorSystem: ActorSystem) extends Manager {
  override def workload: Workload[_, _, _] = new TestWorkload(1)
  override def workers: Workers = new Workers() {
    override def forItem(item: WorkItem[_])(
      implicit ec: ExecutionContext
    ): Future[Worker] = Future.successful(new TestWorker(item))
  }
  override def director: Director = ???
}

object ManagerTestApp extends App {
  implicit val system = ActorSystem("ManagerTestApp")
  implicit val materializer = ActorMaterializer
  implicit val ec = system.dispatcher

  val itemSource = Source(1 to 100).map(i => new TestWorkItem(i))
  lazy val testManager = new TestManager(system)
  val statsTracker = testManager.statsTracker
  val executionGraph =
    new WorkloadExecutionGraph(testManager, itemSource)
  testManager.setWorkerPoolSize(10)

  executionGraph.workSource
    .runFold(0) {
      case (result, _) => result + 1
    }
    .onComplete {
      case Success(n) => println(s"processed ${n} items.")
    }
}
