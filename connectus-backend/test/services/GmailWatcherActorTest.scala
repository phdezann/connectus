package services

import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
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
    implicit val timeout = Timeout(5 seconds)
    val gmailClient = mock[GmailClient]
    val fixedClock = new FixedClock(Instant.now(), ZoneId.of("UTC"))
    val app = getTestGuiceApplicationBuilder //
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[Clock].toInstance(fixedClock))
      .build

    val oneHourInMillis = 3600 * 1000
    val expirationTimeInMillis = fixedClock.instant.toEpochMilli + oneHourInMillis

    val wr = new WatchResponse
    wr.setExpiration(expirationTimeInMillis)
    wr.setHistoryId(BigInt(42).bigInteger)

    when(gmailClient.watch(any, any)) thenReturn fs((wr))
    val gmailWatcherActor = app.injector.instanceOf(BindingKey(classOf[ActorRef]).qualifiedWith(GmailWatcherActor.actorName))

    Await.result(gmailWatcherActor ? StartWatch("me@gmail.com"), Duration.Inf) match {
      case StartWatchDone(_, timeBeforeRenew) =>
        assert(timeBeforeRenew == 3600)
      case _ => fail
    }
  }
}
