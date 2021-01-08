package twita.whipsaw.app.workloads

import akka.actor.ActorSystem
import enumeratum._
import play.api.libs.json.OFormat
import twita.dominion.impl.reactivemongo.MongoContext
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.engine.RegisteredWorkload
import twita.whipsaw.api.engine.RegisteredWorkloads
import twita.whipsaw.api.engine.WorkloadRegistry
import twita.whipsaw.api.engine.WorkloadRegistryEntry
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.app.workloads.processors.AppenderParams
import twita.whipsaw.app.workloads.processors.AppenderToUpperProcessor
import twita.whipsaw.app.workloads.schdulers.ItemCountParams
import twita.whipsaw.app.workloads.schdulers.ToUpperScheduler
import twita.whipsaw.impl.engine.localFunctions.LocalDirector
import twita.whipsaw.impl.reactivemongo.MongoWorkloadRegistryEntry

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AppRegistry {
  implicit def workloadContext: MongoContext with WorkloadContext
  implicit def executionContext: ExecutionContext
  implicit def actorSystem: ActorSystem
  def registeredWorkloads: RegisteredWorkloads

  val workloadDirector: Director = new LocalDirector(AppRegistryEntry, registeredWorkloads)

  sealed trait AppRegistryEntry extends WorkloadRegistryEntry with EnumEntry

  object AppRegistryEntry extends Enum[AppRegistryEntry] with WorkloadRegistry {
    val values = findValues

    override def apply(rw: RegisteredWorkload)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]] = {
      AppRegistryEntry.withName(rw.factoryType).forWorkloadId(rw.id)
    }

    override def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](md: Metadata[Payload, SParams, PParams])(
      implicit executionContext: ExecutionContext
    ): WorkloadFactory[Payload, SParams, PParams] = values.find(_.metadata == md).map(_.factoryForMetadata(md)).get

    case object Sample extends MongoWorkloadRegistryEntry with AppRegistryEntry {
      lazy val metadata = MetadataRegistry.sample
      lazy val factory: WorkloadFactory[_,_,_] = factoryForMetadata(metadata)
    }
  }
}

object MetadataRegistry {
  val sample = Metadata(
      new ToUpperScheduler(_: ItemCountParams)
    , new AppenderToUpperProcessor(_: AppenderParams)
    , Seq("email")
    , "Sample"
  )
}
