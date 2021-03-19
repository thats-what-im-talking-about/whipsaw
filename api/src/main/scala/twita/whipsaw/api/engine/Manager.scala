package twita.whipsaw.api.engine

import java.time.Instant

import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import twita.whipsaw.api.engine.WorkerFactoryActor.SetWorkerPoolSize
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkItem
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatsTracker

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Does the work of "managing" the processing of a particular `Workload`.  The `Manager` instance (of which there
  * may be only one per `Workload`) encapsulates the work that needs to be done in order to process a `Workload`.
  */
trait Manager {

  /**
    * @return Executable `Workload` instance for which this `Manager` has been instantiated.
    */
  def workload: Workload[_, _, _]

  /**
    * @return An abstract representation of the collection of `Worker` instances created by this `Manager`.  Just as
    *         the `Director` instantiates the `Manager`s, the `Manager` in turn instantiates the `Worker` instances that
    *         are responsible for processing an individual `WorkItem`.
    */
  def workers: Workers

  /**
    * @return the `Director` instance which created this `Manager`.
    */
  def director: Director

  /**
    * @return `ActorSystem` to use for the `statsTracker` Actor.
    */
  def actorSystem: ActorSystem

  def setWorkerPoolSize(i: Int) = statsTracker ! SetWorkerPoolSize(i)

  /**
    * Akka Actor that is responsible for asynchronously tracking the stats for the managed Workload.
    */
  lazy val statsTracker = actorSystem.actorOf(
    Props(new WorkloadStatsTracker(this)),
    s"stats-tracker-${workload.id.value}"
  )

  /**
    * Method that is responsible for launching the work that is associated with the managed `Workload`.  Recall that
    * the `Workload` itself defines a `Scheduler` and a `Processor`.  It is this method on the `Manager` that is
    * responsible for actually executing those things.
    *
    * @param m
    * @return A `Future` that will be completed by a Pair of the `SchedulingStatus` and the `ProcessingStatus` for this
    *         `Workload`.
    */
  def executeWorkload()(
    implicit actorSystem: ActorSystem,
    m: Materializer,
    ec: ExecutionContext
  ): Future[(SchedulingStatus, ProcessingStatus)] = {
    director.managers.activate(this)

    val wl = workload // transforms workload into a val, fixes compiler error

    val scheduledItemsFt = workload.schedulingStatus match {
      case SchedulingStatus.Completed =>
        Future.successful(SchedulingStatus.Completed)
      case _ =>
        for {
          _ <- wl(wl.ScheduleStatusUpdated(SchedulingStatus.Running))
          result <- wl.schedule(statsTracker)
          _ <- wl(wl.ScheduleStatusUpdated(result))
        } yield result
    }

    val runnableItems = Source
      .unfoldAsync(None: Option[WorkItem[_]])(
        o =>
          wl.workItems.nextRunnable.flatMap {
            case Some(nextUp) => Future.successful(Some(o -> nextUp))
            case None =>
              wl.workItems.nextRunAt.flatMap {
                case None => Future.successful(None)
                case Some(nextRunAt) =>
                  val sUntilNext =
                    nextRunAt.getEpochSecond() - Instant.now.getEpochSecond
                  sUntilNext match {
                    case soon if soon < 5 =>
                      println(
                        s"sleeping for ${soon + 1}s, the next work item is almost ready!"
                      )
                      Thread.sleep((soon + 1) * 1000)
                      wl.workItems.nextRunnable.map(_.map(o -> _))
                    case later =>
                      println(s"next runAt is in ${later}s, letting go...")
                      Future.successful(None)
                  }
              }
        }
      )

    val processingStatusFt = workload.processingStatus match {
      case ProcessingStatus.Completed =>
        Future.successful(ProcessingStatus.Completed)
      case _ =>
        for {
          _ <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Running))
          graph = new WorkloadExecutionGraph(this, runnableItems)
          _ = setWorkerPoolSize(50)
          result <- graph.workSource.runFold(0) {
            case (result, _) => result + 1
          }
          nextRunAt <- wl.workItems.nextRunAt
          status = nextRunAt match {
            case Some(_) => ProcessingStatus.Waiting
            case _       => ProcessingStatus.Completed
          }
          _ <- wl(wl.ProcessingStatusUpdated(status))
        } yield {
          println(s"processed ${result} items")
          statsTracker ! WorkloadStatsTracker.Deactivate(nextRunAt)
          status
        }
    }

    statsTracker ! wl.stats

    for {
      sStatus <- scheduledItemsFt
      pStatus <- processingStatusFt
    } yield {
      director.managers.deactivate(this)
      (sStatus, pStatus)
    }
  }
}

/**
  * Represents a colleciton of `Manager` instances that are currently active.  The `Managers` instance itself is
  * obtained from the `Director`, and is thus inherently scoped to be all the `Managers` that have been activated
  * by a given `Director`.  In most cases, we would expect a given application to have only one `Director` but there
  * is nothing in this API (nor may there be in the implementation) that precludes us from having multiple.  The only
  * way that the API provides for creating new `Manager` instances is through this `Managers` trait, so it serves
  * both as a repository and as a factory for a given `Director`'s activated `Manager`s.
  */
trait Managers {

  /**
    * @param workload `Workload` instance whose `Manager` we are looking for.
    * @return `Future` that will be completed with a `Manager` instance (either new or previously created)
    *         for the `Workload` provided.
    */
  def forWorkload(workload: Workload[_, _, _]): Future[Manager]

  /**
    * @param workloadId WorkloadId whose Manager we are looking for.
    * @return Future that will be completed with the Manager instance (either new or previously created)
    *         for the workload id provided.
    */
  def forWorkloadId(workloadId: WorkloadId): Future[Manager]

  /**
    * @param workloadId of the `Workload` (or `RegisteredWorkload`) for which we are looking for a `Manager`.
    * @return If one exists, the `Manager` for the associated `WorkloadId` is returned, otherwise None.
    */
  def lookup(workloadId: WorkloadId): Option[Manager]

  /**
    * Adds a `Manager` instance to this `Managers` collection, and then "activates" the managed `Workload`.
    * @param manager `Manager` of the `Workload` to be activated.
    * @return A `Future` that will be completed by the `Manager` instance associated with this `Workload`.  A
    *         `Workload` may have one and only ony `Manager` at a time, and that constraints is enforced here.
    */
  def activate(manager: Manager): Future[Manager]

  /**
    * Removes a Manager instance from this Managers collection, and then "deactivates" the workload.
    * @param manager Manager of the workload to be deactivated
    */
  def deactivate(manager: Manager): Future[Unit]
}
