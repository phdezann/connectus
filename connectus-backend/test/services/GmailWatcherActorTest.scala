package services

import java.time.{Clock, ZoneId, Instant}

import _root_.support.AppConf
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.api.services.gmail.model.WatchResponse
import common._
import org.mockito.Mockito._
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import services.GmailWatcherActor.{StartWatch, StartWatchDone}
import services.support.FixedClock

import scala.concurrent.Await
import scala.concurrent.duration._

class GmailWatcherActorTest extends FunSuiteLike with Mockito {

  test("StartWatch") {
    implicit val timeout = Timeout(5 seconds)
    val appConf = mock[AppConf]
    val googleAuthorization = mock[GoogleAuthorization]
    val gmailClient = mock[GmailClient]
    val firebaseFacade = mock[FirebaseFacade]
    val fixedClock = new FixedClock(Instant.now(), ZoneId.of("UTC"))
    val app = new GuiceApplicationBuilder() //
      .overrides(bind[GoogleAuthorization].toInstance(googleAuthorization))
      .overrides(bind[AppConf].toInstance(appConf))
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[FirebaseFacade].toInstance(firebaseFacade))
      .overrides(bind[Clock].toInstance(fixedClock))
      .build

    val oneHourInMillis = 3600 * 1000
    val expirationTimeInMillis = fixedClock.instant.toEpochMilli + oneHourInMillis

    val wr = new WatchResponse
    wr.setExpiration(expirationTimeInMillis)
    wr.setHistoryId(BigInt(42).bigInteger)

    when(gmailClient.watch(any)) thenReturn fs((wr))
    val gmailWatcherActor = app.injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith("gmailWatcherActor"))

    Await.result(gmailWatcherActor ? StartWatch("me@gmail.com"), Duration.Inf) match {
      case StartWatchDone(_, timeBeforeRenew, totalWatcherCount) =>
        assert(timeBeforeRenew == 3600)
        assert(totalWatcherCount == 1)
      case _ => fail
    }
  }
}
