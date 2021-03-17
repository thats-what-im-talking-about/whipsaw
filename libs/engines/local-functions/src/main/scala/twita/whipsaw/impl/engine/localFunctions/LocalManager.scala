package twita.whipsaw.impl.engine.localFunctions

import akka.actor.ActorSystem
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.engine.Managers
import twita.whipsaw.api.engine.Workers
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LocalManager[Attr](
  override val director: Director[Attr],
  override val workload: Workload[_, _, _]
)(implicit val executionContext: ExecutionContext, val actorSystem: ActorSystem)
    extends Manager[Attr] {
  override def workers: Workers = new LocalWorkers
}

class LocalManagers[Attr](val director: Director[Attr])(
  implicit executionContext: ExecutionContext,
  actorSystem: ActorSystem
) extends Managers[Attr] {
  private lazy val _managers = mutable.Map.empty[WorkloadId, Manager[Attr]]

  override def forWorkload(workload: Workload[_, _, _]): Future[Manager[Attr]] =
    Future.successful(new LocalManager(director, workload))

  override def forWorkloadId(workloadId: WorkloadId): Future[Manager[Attr]] =
    director.registeredWorkloads
      .get(DomainObjectGroup.byId(workloadId))
      .flatMap {
        case Some(rw) =>
          director.registry(rw).map(new LocalManager(director, _))
        case _ =>
          Future.failed(
            new IllegalStateException(s"WorkloadId not found: ${workloadId}")
          )
      }

  override def lookup(workloadId: WorkloadId): Option[Manager[Attr]] =
    _managers.get(workloadId)

  /**
    * Adds a Manager instance to this Managers collection, and then "activates" the workload.  If there is already
    * a Manager present for the workload, we just return that manager as-is (without invoking the workload).
    *
    * @param manager Manager of the workload to be activated
    */
  override def activate(manager: Manager[Attr]): Future[Manager[Attr]] =
    lookup(manager.workload.id) match {
      case Some(m) => Future.successful(m)
      case None =>
        _managers.put(manager.workload.id, manager)
        manager.executeWorkload().map(_ => manager)
    }

  /**
    * Removes a Manager instance from this Managers collection, and then "deactivates" the workload.
    *
    * @param manager Manager of the workload to be deactivated
    */
  override def deactivate(manager: Manager[Attr]): Future[Unit] =
    Future.successful(_managers.remove(manager.workload.id))
}
