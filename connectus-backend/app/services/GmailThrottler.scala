package services

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef, Props, _}
import akka.contrib.throttle.Throttler.{SetTarget, _}
import akka.contrib.throttle.TimerBasedThrottler
import akka.pattern.{ask, pipe}
import com.google.api.services.gmail.{Gmail, GmailRequest}
import common.Email
import services.GmailRequests._

import scala.concurrent._
import scala.language.postfixOps

object GmailRequests {
  case class ListHistoryRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class CreateLabelRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class DeleteLabelRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetLabelRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListLabelsRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetMessageRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListMessagesRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ModifyMessageRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class SendMessageRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetThreadRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListThreadsRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class WatchRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
}

class GmailThrottlerClient @Inject()(@Named(GmailThrottlerActor.actorName) googleClientThrottlerActor: ActorRef, userActorClient: UserActorClient) {
  implicit val timeout = Timeouts.oneMinute

  import scala.concurrent.ExecutionContext.Implicits.global

  def schedule[T](email: Email, request: GmailRequest[T], msgBuilder: (() => Future[_], Option[ActorRef]) => Any): Future[Option[T]] = {
    def execute[T](request: => GmailRequest[T]) = () => Future {concurrent.blocking {request.execute}}
    userActorClient.getGmailThrottlerActor(email).flatMap { actorOpt =>
      if (actorOpt.isDefined) {
        (actorOpt.get ? msgBuilder(execute(request), None)).asInstanceOf[Future[Option[T]]]
      } else {
        execute(request)().map(Option(_))
      }
    }
  }

  def scheduleListHistory(email: Email, request: Gmail#Users#History#List) = schedule(email, request, ListHistoryRequestMsg.apply).map(_.get)
  def scheduleCreateLabel(email: Email, request: Gmail#Users#Labels#Create) = schedule(email, request, CreateLabelRequestMsg.apply).map(_.get)
  def scheduleDeleteLabel(email: Email, request: Gmail#Users#Labels#Delete) = schedule(email, request, DeleteLabelRequestMsg.apply).map(_ => ())
  def scheduleGetLabel(email: Email, request: Gmail#Users#Labels#Get) = schedule(email, request, GetLabelRequestMsg.apply).map(_.get)
  def scheduleListLabels(email: Email, request: Gmail#Users#Labels#List) = schedule(email, request, ListLabelsRequestMsg.apply).map(_.get)
  def scheduleGetMessage(email: Email, request: Gmail#Users#Messages#Get) = schedule(email, request, GetMessageRequestMsg.apply).map(_.get)
  def scheduleListMessages(email: Email, request: Gmail#Users#Messages#List) = schedule(email, request, ListMessagesRequestMsg.apply).map(_.get)
  def scheduleModifyMessage(email: Email, request: Gmail#Users#Messages#Modify) = schedule(email, request, GetMessageRequestMsg.apply).map(_.get)
  def scheduleSendMessage(email: Email, request: Gmail#Users#Messages#Send) = schedule(email, request, SendMessageRequestMsg.apply).map(_.get)
  def scheduleGetThread(email: Email, request: Gmail#Users#Threads#Get) = schedule(email, request, GetThreadRequestMsg.apply).map(_.get)
  def scheduleListThreads(email: Email, request: Gmail#Users#Threads#List) = schedule(email, request, ListThreadsRequestMsg.apply).map(_.get)
  def scheduleWatch(email: Email, request: Gmail#Users#Watch) = schedule(email, request, WatchRequestMsg.apply).map(_.get)
}

object ThrottlerUtils {
  def createThrottler(context: ActorContext, props: Props, rate: Rate): ActorRef = {
    val actor = context.actorOf(Props(new TimerBasedThrottler(rate)))
    actor ! SetTarget(Some(context.actorOf(props)))
    actor
  }
}

object GmailThrottlerActor {
  final val actorName = "gmailThrottlerActor"
}

class GmailThrottlerActor extends Actor with ActorLogging {
  val dispatcher = ThrottlerUtils.createThrottler(context, Props(new DispatcherActor), 250 msgsPerSecond)
  override def receive: Receive = {
    case request@ListHistoryRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@CreateLabelRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@DeleteLabelRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetLabelRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListLabelsRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetMessageRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListMessagesRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ModifyMessageRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@SendMessageRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetThreadRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListThreadsRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@WatchRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
  }
}

class DispatcherActor extends Actor with ActorLogging {
  // https://developers.google.com/gmail/api/v1/reference/quota
  val listHistoryActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 2 msgsPerSecond)
  val createLabelActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val deleteLabelActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val getLabelActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 1 msgsPerSecond)
  val listLabelsActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 1 msgsPerSecond)
  val getMessageActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val listMessagesActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val sendMessageActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 100 msgsPerSecond)
  val modifyMessagesActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val getThreadActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 10 msgsPerSecond)
  val listThreadsActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 10 msgsPerSecond)
  val watchActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 100 msgsPerSecond)

  override def receive: Receive = {
    case request@ListHistoryRequestMsg(_, _) => listHistoryActor ! request
    case request@CreateLabelRequestMsg(_, _) => createLabelActor ! request
    case request@DeleteLabelRequestMsg(_, _) => deleteLabelActor ! request
    case request@GetLabelRequestMsg(_, _) => getLabelActor ! request
    case request@ListLabelsRequestMsg(_, _) => listLabelsActor ! request
    case request@GetMessageRequestMsg(_, _) => getMessageActor ! request
    case request@ListMessagesRequestMsg(_, _) => listMessagesActor ! request
    case request@SendMessageRequestMsg(_, _) => sendMessageActor ! request
    case request@ModifyMessageRequestMsg(_, _) => modifyMessagesActor ! request
    case request@GetThreadRequestMsg(_, _) => getThreadActor ! request
    case request@ListThreadsRequestMsg(_, _) => listThreadsActor ! request
    case request@WatchRequestMsg(_, _) => watchActor ! request
  }
}

class SchedulerActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case ListHistoryRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case CreateLabelRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case DeleteLabelRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetLabelRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListLabelsRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetMessageRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListMessagesRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case SendMessageRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ModifyMessageRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetThreadRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListThreadsRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case WatchRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
  }
}

class RequestExecutor(request: () => Future[_], client: ActorRef) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher
  pipe(request().map(Option(_))) to self

  override def receive: Receive = {
    case Status.Failure(f) =>
      client ! Status.Failure(f)
      context.stop(self)
    case response =>
      client ! response
      context.stop(self)
  }
}
