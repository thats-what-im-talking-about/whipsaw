package twita.whipsaw.impl.engine.localFunctions

import akka.actor.ActorSystem
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.engine.Managers
import twita.whipsaw.api.engine.RegisteredWorkloads
import twita.whipsaw.api.engine.WorkloadRegistry

import scala.concurrent.ExecutionContext

class LocalDirector(
    override val registry: WorkloadRegistry
  , override val registeredWorkloads: RegisteredWorkloads
)(implicit val executionContext: ExecutionContext, actorSystem: ActorSystem) extends Director {

  /**
    * @return Managers that are currently working on a Workload at the request of this Director.
    */
  override lazy val managers: Managers = new LocalManagers(this)
}
