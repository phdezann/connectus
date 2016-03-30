package services

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import _root_.support.AppConf
import akka.actor.ActorRef
import akka.pattern.{FutureTimeoutSupport, ask}
import akka.util.Timeout
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, _}
import services.GmailRequests._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}

class GmailThrottlerTest extends FunSuiteLike with Mockito with FutureTimeoutSupport {
  test("Throttle Gmail API calls") {
    implicit val timeout = Timeout(5 seconds)

    val appConf = mock[AppConf]
    val googleAuthorization = mock[GoogleAuthorization]
    val gmailClient = mock[GmailClient]
    val autoTagger = mock[AutoTagger]

    val firebaseFacade = mock[FirebaseFacade]
    val injector = new GuiceApplicationBuilder()
      .overrides(bind[GoogleAuthorization].toInstance(googleAuthorization))
      .overrides(bind[AppConf].toInstance(appConf))
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[AutoTagger].toInstance(autoTagger))
      .overrides(bind[FirebaseFacade].toInstance(firebaseFacade))
      .build.injector

    val gmailClientThrottlerActor = injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(GmailRequests.actorName))

    val f1 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})
    val f2 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})
    val f3 = gmailClientThrottlerActor ? ListLabelsRequestMsg(() => Future {LocalDateTime.now()})

    val sequence = Future.sequence(List(f1, f2, f3))
    Await.result(sequence, Duration.Inf) match {
      case List(jr1: LocalDateTime, jr2: LocalDateTime, jr3: LocalDateTime) =>
        assert(jr1.isBefore(jr2))
        assert(jr2.isBefore(jr3))
        assert(jr1.until(jr2, ChronoUnit.SECONDS) == 1)
        assert(jr1.until(jr3, ChronoUnit.SECONDS) == 2)
      case e => fail
    }
  }
}
