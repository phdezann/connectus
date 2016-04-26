package services

import java.time.LocalDateTime

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import common._
import services.JobQueueActor.{Job, SkippedJob}
import services.support.TestBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

class JobQueueActorTest extends TestBase {
  test("execute futures serially") {
    implicit val timeout = Timeouts.oneMinute

    val injector = getTestGuiceApplicationBuilder.build.injector
    val actorSystem = injector.instanceOf(classOf[ActorSystem])
    val jobQueueActor = actorSystem.actorOf(identity(Props(injector.instanceOf[JobQueueActor])))

    case class JobResult(start: LocalDateTime, end: LocalDateTime)
    def enqueueNewJob: Future[Any] = {
      (jobQueueActor ? Job(() => completeSoon(actorSystem, LocalDateTime.now))).mapTo[Try[LocalDateTime]].map {
        case Success(start) => JobResult(start, LocalDateTime.now())
        case _ => ()
      }
    }
    val sequence = Future.sequence(List(enqueueNewJob, enqueueNewJob, enqueueNewJob))
    Await.result(sequence, Duration.Inf) match {
      case List(jr1: JobResult, jr2: JobResult, jr3: JobResult) =>
        assert(jr1.end.isBefore(jr2.end) || jr1.end.isEqual(jr2.end))
        assert(jr2.end.isBefore(jr3.end) || jr2.end.isEqual(jr3.end))
      case e => fail
    }
  }

  test("skip already scheduled job") {
    implicit val timeout = Timeouts.oneMinute

    val injector = getTestGuiceApplicationBuilder.build.injector
    val actorSystem = injector.instanceOf(classOf[ActorSystem])
    val jobQueueActor = actorSystem.actorOf(identity(Props(injector.instanceOf[JobQueueActor])))

    def enqueueNewJob(value: String, key: String): Future[Any] =
      jobQueueActor ? Job(() => completeSoon(actorSystem, value), Some(key))

    val f1 = enqueueNewJob("A1", "keyA")
    val f2 = enqueueNewJob("A2", "keyA")
    val f3 = enqueueNewJob("A3", "keyA")
    val f4 = enqueueNewJob("B1", "keyB")
    val f5 = enqueueNewJob("B2", "keyB")

    val sequence = Future.sequence(List(f1, f2, f3, f4, f5))
    Await.result(sequence, Duration.Inf) match {
      case List(Success("A1"), Success("A2"), Success(SkippedJob), Success("B1"), Success(SkippedJob)) =>
      case e => fail
    }
  }

  private def completeSoon[T](actorSystem: ActorSystem, value: T): Future[T] = {
    val delay = 100.millis
    after(delay, actorSystem.scheduler)(fs(value))
  }
}
