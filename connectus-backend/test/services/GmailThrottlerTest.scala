package services

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import services.GmailRequests._
import services.support.TestBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class GmailThrottlerTest extends TestBase {
  test("Throttle Gmail API calls") {
    implicit val timeout = Timeouts.oneMinute
    val injector = getTestGuiceApplicationBuilder.build.injector
    val gmailClientThrottlerActor = injector.instanceOf[ActorSystem].actorOf(identity(Props(injector.instanceOf[GmailThrottlerActor])))

    val f1 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})
    val f2 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})
    val f3 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})

    val sequence = Future.sequence(List(f1, f2, f3))
    Await.result(sequence, Duration.Inf) match {
      case List(Some(jr1:LocalDateTime), Some(jr2:LocalDateTime), Some(jr3:LocalDateTime)) =>
        assert(jr1.isBefore(jr2))
        assert(jr2.isBefore(jr3))
        assert(jr1.until(jr2, ChronoUnit.SECONDS) == 1)
        assert(jr1.until(jr3, ChronoUnit.SECONDS) == 2)
      case e => fail
    }
  }
}
