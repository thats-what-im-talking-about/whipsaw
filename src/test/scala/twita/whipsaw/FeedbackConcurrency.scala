package twita.whipsaw

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.stream.ActorMaterializer
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.SourceShape
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Zip

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Random
import scala.util.Success

/**
  * This is really just a scratch file that I used to build up an Akka Stream that I could use to prove out the
  * idea that I could build a stream that allows for the number of workers to change during the course of
  * processing.
  */
object FeedbackConcurrency extends App {
  implicit val system = ActorSystem("FeedbackConcurrency")
  implicit val materializer = ActorMaterializer

  val itemSource =
    Source.fromIterator(() => Iterator.range(1, 100).map(i => s"payload ${i}"))

  case class Worker(id: Int)

  sealed trait WorkerFactoryProtocol
  case class WorkersAvailable(workers: List[Worker])
      extends WorkerFactoryProtocol
  case class SetWorkerPoolSize(to: Int) extends WorkerFactoryProtocol
  case object ShuttingDown extends WorkerFactoryProtocol
  case object ShutDownTimeout extends WorkerFactoryProtocol

  class WorkerFactoryActor extends Actor with ActorLogging {
    override def receive: Receive = availableWorkers(0, 0, 0)

    def availableWorkers(max: Int, current: Int, currentId: Int): Receive = {
      case WorkersAvailable(freedWorkers) =>
        val numWorkersToEnqueue =
          Math.max(0, max - (current - freedWorkers.size))
        val existingWorkers = freedWorkers.take(numWorkersToEnqueue)
        val numNewWorkers = numWorkersToEnqueue - existingWorkers.size
        val allWorkersToEnqueue = existingWorkers ++ Range(0, numNewWorkers)
          .map(i => Worker(i + currentId))
        val newCurrent = current - freedWorkers.size + numWorkersToEnqueue
        val newCurrentId = currentId + numNewWorkers

        allWorkersToEnqueue.foreach(workerQueue.offer)
        context.become(availableWorkers(max, newCurrent, newCurrentId))

      case SetWorkerPoolSize(newMax) =>
        self ! WorkersAvailable(List())
        context.become(availableWorkers(newMax, current, currentId))

      case ShuttingDown =>
        val shutdownTimout = context.system.scheduler
          .scheduleOnce(10.minute, self, ShutDownTimeout)
        context.become(shuttingDown(max, current, shutdownTimout))
    }

    def shuttingDown(max: Int,
                     current: Int,
                     shutdownTimout: Cancellable): Receive = {
      case WorkersAvailable(freedWorkers) =>
        val newNumWorkers = current - freedWorkers.size
        newNumWorkers match {
          case 0 =>
            log.info("workers are completed, shutting down")
            shutdownTimout.cancel()
            self ! akka.actor.Status.Success(CompletionStrategy.draining)
            workCompletedActor !
              akka.actor.Status.Success(CompletionStrategy.draining)
          case n =>
            context.become(shuttingDown(max, newNumWorkers, shutdownTimout))
        }
      case ShutDownTimeout =>
        self ! akka.actor.Status.Success(CompletionStrategy.draining)
        workCompletedActor !
          akka.actor.Status.Success(CompletionStrategy.draining)
      case _ =>
    }
  }
  val workerFactoryActor =
    system.actorOf(Props[WorkerFactoryActor], "WorkerFactory")

  val workerQueueInit =
    Source.queue[Worker](10, OverflowStrategy.dropHead)
  val (workerQueue, workerQueueSource) = workerQueueInit.preMaterialize()
  workerQueue.watchCompletion().onComplete {
    case Success(t) =>
      println("queue is done!")
      workerFactoryActor ! ShuttingDown
    case Failure(t) =>
      t.printStackTrace()
      workerFactoryActor ! ShuttingDown
  }

  val workCompletedActorInit =
    Source.actorRef[(String, Worker)](10, OverflowStrategy.dropHead)
  val (workCompletedActor, workCompletedSource) =
    workCompletedActorInit.preMaterialize()

  val workSource = Source.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[String, Worker])
    val processorSink = Sink.foreach[(String, Worker)] {
      case (payload, worker) =>
        Future {
          val processingTime = Random.nextInt(2000)
          //val processingTime = 2000
          Thread.sleep(processingTime)
          worker -> s"${payload} processed by ${worker} in ${processingTime}ms"
        }.map {
          case r @ (worker, _) =>
            workerFactoryActor ! WorkersAvailable(List(worker))
            workCompletedActor ! r
        }
    }
    val workCompletedSourceShape = builder.add(workCompletedSource)

    // scalastyle: off
    // format: off
    itemSource   ~> zip.in0
    workerQueueSource ~> zip.in1 ; zip.out ~> processorSink ; 
    // format: on
    // scalastyle: on

    SourceShape(workCompletedSourceShape.out)
  })

  val result = workSource
    .runWith(Sink.fold(0) { (result, _) =>
      println(s"just procssed result $result")
      result + 1
    })
    .onComplete {
      case Success(i) => println(s"finished processing ${i} results")
      case Failure(t) => t.printStackTrace()
    }
  Thread.sleep(3000)
  workerFactoryActor ! SetWorkerPoolSize(5)
  Thread.sleep(3000)
  workerFactoryActor ! SetWorkerPoolSize(1)
  Thread.sleep(3000)
  workerFactoryActor ! SetWorkerPoolSize(0)
  Thread.sleep(10000)
  workerFactoryActor ! SetWorkerPoolSize(5)
  Thread.sleep(3000)
}
