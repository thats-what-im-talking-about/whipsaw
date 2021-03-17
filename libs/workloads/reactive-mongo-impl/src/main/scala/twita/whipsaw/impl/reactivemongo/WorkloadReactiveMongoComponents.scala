package twita.whipsaw.impl.reactivemongo

import play.api.libs.json.OFormat
import twita.dominion.impl.reactivemongo.DevMongoContextImpl
import twita.dominion.impl.reactivemongo.MongoContext
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.workloads.WorkloadContext

import scala.concurrent.ExecutionContext

trait WorkloadReactiveMongoComponents[Attr] {
  implicit def attrFmt: OFormat[Attr]
  implicit def executionContext: ExecutionContext

  implicit lazy val workloadContext: MongoContext with WorkloadContext =
    new DevMongoContextImpl with WorkloadContext
  lazy val registeredWorkloads: RegisteredWorkloads[Attr] =
    new MongoRegisteredWorkloads[Attr]
}
