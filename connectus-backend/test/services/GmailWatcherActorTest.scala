package services

import java.time.{Clock, Instant, ZoneId}

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import com.google.api.services.gmail.model.WatchResponse
import common._
import org.mockito.Mockito._
import play.api.inject._
import services.GmailWatcherActor.{StartWatch, StartWatchDone}
import services.support.{FixedClock, TestBase}

import scala.concurrent.Await
import scala.concurrent.duration._

class GmailWatcherActorTest extends TestBase {

  test("StartWatch") {
    implicit val timeout = Timeouts.oneMinute
    val gmailClient = mock[GmailClient]
    val fixedClock = new FixedClock(Instant.now(), ZoneId.of("UTC"))
    val injector = getTestGuiceApplicationBuilder //
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[Clock].toInstance(fixedClock))
      .build.injector

    val oneHourInMillis = 3600 * 1000
    val expirationTimeInMillis = fixedClock.instant.toEpochMilli + oneHourInMillis

    val wr = new WatchResponse
    wr.setExpiration(expirationTimeInMillis)
    wr.setHistoryId(BigInt(42).bigInteger)

    when(gmailClient.watch(any, any)) thenReturn fs((wr))
    val gmailWatcherActor = injector.instanceOf[ActorSystem].actorOf(identity(Props(injector.instanceOf[GmailWatcherActor])))

    Await.result(gmailWatcherActor ? StartWatch("me@gmail.com"), Duration.Inf) match {
      case StartWatchDone(_, timeBeforeRenew) =>
        assert(timeBeforeRenew == 3600)
      case _ => fail
    }
  }
}
