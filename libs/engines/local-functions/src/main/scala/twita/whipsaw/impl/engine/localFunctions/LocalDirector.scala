package twita.whipsaw.impl.engine.localFunctions

import akka.actor.ActorSystem
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.engine.Managers
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.registry.WorkloadRegistry

import scala.concurrent.ExecutionContext

class LocalDirector[Attr](
  override val registry: WorkloadRegistry[Attr],
  override val registeredWorkloads: RegisteredWorkloads[Attr]
)(implicit val executionContext: ExecutionContext, actorSystem: ActorSystem)
    extends Director[Attr] {

  /**
    * @return Managers that are currently working on a Workload at the request of this Director.
    */
  override lazy val managers: Managers[Attr] = new LocalManagers[Attr](this)
}
