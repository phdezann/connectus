package services

import java.time.temporal.ChronoUnit
import java.time.{Clock, LocalDateTime}
import javax.inject.Inject

import akka.actor.{Actor, _}
import akka.pattern.pipe
import common.Email
import model.GmailWatchReply
import play.api.Logger
import services.GmailWatcherActor._

import scala.concurrent.duration._
import scala.language.postfixOps

object GmailWatcherActor {
  final val actorName = "gmailWatcherActor"
  case class StartWatch(email: Email)
  case class WatchResponsePacket(email: Email, gmailWatchReply: GmailWatchReply, client: ActorRef)
  case class StartWatchDone(email: Email, timeBeforeRenew: Long)
}

class GmailWatcherActor @Inject()(clock: Clock, mailClient: MailClient) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher

  var renew: Option[akka.actor.Cancellable] = None

  override def receive: Receive = {
    case StartWatch(email) =>
      Logger.info(s"StartWatch for $email")
      val s = sender
      pipe(mailClient.watch(email, List(LabelService.InboxLabelName)).map(WatchResponsePacket(email, _, s))) to self
    case WatchResponsePacket(email, gmailWatchReply, client) =>
      val now = LocalDateTime.now(clock)
      val timeBeforeRenew = now.until(gmailWatchReply.expirationDate, ChronoUnit.SECONDS)
      renew = Some(context.system.scheduler.scheduleOnce(timeBeforeRenew seconds)(self ! StartWatch(email)))
      client ! StartWatchDone(email, timeBeforeRenew)
      Logger.info(s"WatchResponse received for $email with expiration date ${gmailWatchReply.expirationDate} and historyId ${gmailWatchReply.historyId}")
    case Status.Failure(e) =>
      Logger.error(s"Watch failed", e)
      context.stop(self)
  }

  override def postStop(): Unit = {
    renew.fold(())(_.cancel)
  }
}
