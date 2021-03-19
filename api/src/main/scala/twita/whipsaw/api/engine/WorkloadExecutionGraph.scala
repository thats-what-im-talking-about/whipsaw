package twita.whipsaw.api.engine

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import akka.stream.CompletionStrategy
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.SourceShape
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.scaladsl.Zip
import akka.util.Timeout
import twita.whipsaw.api.engine.WorkerFactoryActor.SetWorkerPoolSize
import twita.whipsaw.api.engine.WorkerFactoryActor.ShutDownTimeout
import twita.whipsaw.api.engine.WorkerFactoryActor.ShuttingDown
import twita.whipsaw.api.engine.WorkerFactoryActor.WorkerActivated
import twita.whipsaw.api.engine.WorkerFactoryActor.WorkersAvailable
import twita.whipsaw.api.engine.WorkloadExecutionGraph.WorkerSlot
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.WorkItem
import twita.whipsaw.monitor.WorkloadStatsTracker.SetWorkerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object WorkloadExecutionGraph {
  // token that will be passed around to represent an available worker in our worker pool.
  case class WorkerSlot(id: Int)
}

object WorkerFactoryActor {
  sealed trait Protocol
  case class WorkersAvailable(workers: List[WorkerSlot]) extends Protocol
  case class SetWorkerPoolSize(to: Int) extends Protocol
  case class WorkerActivated(w: WorkerSlot) extends Protocol
  case object ShuttingDown extends Protocol
  case object ShutDownTimeout extends Protocol
}

class WorkerFactoryActor(workerQueue: SourceQueueWithComplete[WorkerSlot],
                         workCompletedActor: ActorRef,
                         statsTracker: ActorRef)
    extends Actor
    with ActorLogging {
  implicit lazy val executionContext = context.dispatcher

  override def receive: Receive = availableWorkers(0, 0, 0, 0)

  override def preStart() = statsTracker ! SetWorkerFactory(self)

  def availableWorkers(max: Int,
                       current: Int,
                       active: Int,
                       currentId: Int): Receive = {
    case WorkersAvailable(freedWorkers) =>
      val numWorkersToEnqueue =
        Math.max(0, max - (current - freedWorkers.size))
      val existingWorkers = freedWorkers.take(numWorkersToEnqueue)
      val numNewWorkers = numWorkersToEnqueue - existingWorkers.size
      val allWorkersToEnqueue = existingWorkers ++ Range(0, numNewWorkers)
        .map(i => WorkerSlot(i + currentId))
      val newCurrent = current - freedWorkers.size + numWorkersToEnqueue
      val newActive = active - freedWorkers.size
      val newCurrentId = currentId + numNewWorkers

      allWorkersToEnqueue.foreach(workerQueue.offer)
      context.become(availableWorkers(max, newCurrent, newActive, newCurrentId))

    case WorkerActivated(t) =>
      context.become(availableWorkers(max, current, active + 1, currentId))

    case SetWorkerPoolSize(newMax) =>
      log.info(
        s"new max workers is ${newMax}, current: ${current}, active: ${active}"
      )
      self ! WorkersAvailable(List())
      context.become(availableWorkers(newMax, current, active, currentId))

    case ShuttingDown =>
      val shutdownTimout = context.system.scheduler
        .scheduleOnce(10.minute, self, ShutDownTimeout)
      self ! WorkersAvailable(List())
      context.become(shuttingDown(max, active, shutdownTimout))
  }

  def shuttingDown(max: Int,
                   active: Int,
                   shutdownTimout: Cancellable): Receive = {
    case WorkersAvailable(freedWorkers) =>
      val newActive = active - freedWorkers.size
      newActive match {
        case 0 =>
          log.info("workers are completed, shutting down")
          shutdownTimout.cancel()
          self ! akka.actor.Status.Success(CompletionStrategy.draining)
          context.system.scheduler.scheduleOnce(
            2.second,
            workCompletedActor,
            akka.actor.Status.Success(CompletionStrategy.draining)
          )
        case n =>
          context.become(shuttingDown(max, newActive, shutdownTimout))
      }
    case ShutDownTimeout =>
      self ! akka.actor.Status.Success(CompletionStrategy.draining)
      workCompletedActor !
        akka.actor.Status.Success(CompletionStrategy.draining)
    case _ =>
  }
}

/**
  * Contains all of the scaffolding needed to set up a graph for executing a Workload.
  */
class WorkloadExecutionGraph(manager: Manager,
                             itemSource: Source[WorkItem[_], _])(
  implicit system: ActorSystem,
  materializer: Materializer,
  executionContext: ExecutionContext
) {
  lazy val workerQueueInit =
    Source.queue[WorkerSlot](1000, OverflowStrategy.backpressure)
  lazy val (workerQueue, workerQueueSource) = workerQueueInit.preMaterialize()
  workerQueue.watchCompletion().onComplete {
    case Success(t) =>
      workerFactoryActor ! ShuttingDown
    case Failure(t) =>
      t.printStackTrace()
      workerFactoryActor ! ShuttingDown
  }

  lazy val workCompletedActorInit =
    Source.actorRef[ItemResult](1000, OverflowStrategy.dropHead)
  lazy val (workCompletedActor, workCompletedSource) =
    workCompletedActorInit.preMaterialize()

  val workerFactoryActor =
    system.actorOf(
      Props(
        new WorkerFactoryActor(
          workerQueue,
          workCompletedActor,
          manager.statsTracker,
        )
      )
    )

  lazy val workSource = Source.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    implicit val timeout = Timeout(10.seconds)

    val zip = builder.add(Zip[WorkItem[_], WorkerSlot])
    val processorSink = Sink.foreach[(WorkItem[_], WorkerSlot)] {
      case (item, workerToken) =>
        (for {
          worker <- manager.workers.forItem(item)
          _ = workerFactoryActor ! WorkerActivated(workerToken)
          _ = item.updateStatsBeforeProcessing(manager.statsTracker)
          result <- worker.process()
          _ = result.updateStatsWithResult(manager.statsTracker)
        } yield {
          workerFactoryActor ! WorkersAvailable(List(workerToken))
          workCompletedActor ! result
        }).recover {
          case t: Throwable => t.printStackTrace()
        }
    }
    val workCompletedSourceShape = builder.add(workCompletedSource)

    itemSource ~> zip.in0
    workerQueueSource ~> zip.in1
    zip.out ~> processorSink

    SourceShape(workCompletedSourceShape.out)
  })
}
