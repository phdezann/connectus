package services

import _root_.support.AppConf
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{FutureTimeoutSupport, ask}
import akka.util.Timeout
import common._
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, _}
import services.JobQueueActor.{JobResult, ScheduledJob}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class JobQueueActorTest extends FunSuiteLike with Mockito with FutureTimeoutSupport {
  test("execute futures serially") {
    implicit val timeout = Timeout(5 seconds)

    val appConf = mock[AppConf]
    val googleAuthorization = mock[GoogleAuthorization]
    val gmailClient = mock[GmailClient]
    val firebaseFacade = mock[FirebaseFacade]
    val injector = new GuiceApplicationBuilder()
      .overrides(bind[GoogleAuthorization].toInstance(googleAuthorization))
      .overrides(bind[AppConf].toInstance(appConf))
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[FirebaseFacade].toInstance(firebaseFacade))
      .build.injector

    val futureJobQueueActor = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith("futureJobQueueActor"))
    val actorSystem = injector.instanceOf(classOf[ActorSystem])

    def completeSoon = {
      val delay = 100.millis
      after(delay, actorSystem.scheduler)(fs(()))
    }

    val f1 = futureJobQueueActor ? ScheduledJob(() => completeSoon)
    val f2 = futureJobQueueActor ? ScheduledJob(() => completeSoon)
    val f3 = futureJobQueueActor ? ScheduledJob(() => completeSoon)

    val sequence = Future.sequence(List(f1, f2, f3))
    Await.result(sequence, Duration.Inf) match {
      case List(jr1: JobResult, jr2: JobResult, jr3: JobResult) =>
        assert(jr1.end.isBefore(jr2.start) || jr1.end.isEqual(jr2.start))
        assert(jr2.end.isBefore(jr3.start) || jr2.end.isEqual(jr3.start))
      case e => fail
    }
  }
}
