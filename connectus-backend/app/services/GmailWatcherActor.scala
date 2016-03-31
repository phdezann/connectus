package services

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import akka.actor.{Actor, _}
import akka.pattern.pipe
import com.google.api.services.gmail.model.WatchResponse
import com.google.inject.Inject
import common.Email
import play.api.Logger
import services.GmailWatcherActor._

import scala.concurrent.duration._
import scala.language.postfixOps

object GmailWatcherActor {
  case class StartWatch(email: Email)
  case class StopWatch(email: Email)
  case class WatchResponsePacket(email: Email, watchResponse: WatchResponse, client: ActorRef)

  case class StartWatchDone(email: Email, timeBeforeRenew: Long, totalWatcherCount: Int)
  case class StopWatchDone(email: Email, totalWatcherCount: Int)
}

class GmailWatcherActor @Inject()(clock: Clock, firebaseFacade: FirebaseFacade, gmailClient: GmailClient) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher

  var renews = Map[Email, Cancellable]()

  firebaseFacade.listenUsers(
    onAddListener = email => self ! StartWatch(email),
    onRemovedListener = email => self ! StopWatch(email))

  override def receive: Receive = {
    case StartWatch(email) =>
      Logger.info(s"StartWatch for $email")
      val s = sender
      pipe(gmailClient.watch(email).map(WatchResponsePacket(email, _, s))) to self
    case WatchResponsePacket(email, watchResponse, client) =>
      val now = LocalDateTime.now(clock)
      val watchResponseExpirationDate = watchResponse.getExpiration
      val expirationDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(watchResponseExpirationDate), ZoneOffset.UTC);
      val historyId = watchResponse.getHistoryId
      val timeBeforeRenew = now.until(expirationDate, ChronoUnit.SECONDS)
      renews = renews + (email -> context.system.scheduler.scheduleOnce(timeBeforeRenew seconds)(self ! StartWatch(email)))
      client ! StartWatchDone(email, timeBeforeRenew, renews.size)
      Logger.info(s"WatchResponse received for $email with expiration date ($watchResponseExpirationDate|$expirationDate) and historyId ${historyId}")
    case StopWatch(email) =>
      renews(email).cancel
      renews = renews - email
      sender ! StopWatchDone
      Logger.info(s"StopWatch for $email")
  }
}
