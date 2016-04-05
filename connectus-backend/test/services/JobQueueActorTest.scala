package services

import java.time.LocalDateTime

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import common._
import play.api.inject.BindingKey
import services.JobQueueActor.Job
import services.support.TestBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

class JobQueueActorTest extends TestBase {
  test("execute futures serially") {
    implicit val timeout = Timeouts.oneMinute

    val injector = getTestGuiceApplicationBuilder.build.injector
    val jobQueueActor = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(JobQueueActor.actorName))
    val actorSystem = injector.instanceOf(classOf[ActorSystem])

    def completeSoon: Future[LocalDateTime] = {
      val delay = 100.millis
      after(delay, actorSystem.scheduler)(fs(LocalDateTime.now()))
    }

    case class JobResult(start: LocalDateTime, end: LocalDateTime)
    def enqueueNewJob: Future[Any] = {
      (jobQueueActor ? Job(() => completeSoon)).mapTo[Try[LocalDateTime]].map {
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
}
