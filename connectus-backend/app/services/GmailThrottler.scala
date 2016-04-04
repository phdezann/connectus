package services

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef, Props, _}
import akka.contrib.throttle.Throttler.{SetTarget, _}
import akka.contrib.throttle.TimerBasedThrottler
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.google.api.services.gmail.{Gmail, GmailRequest}
import services.GmailRequests._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

object GmailRequests {
  case class CreateLabelRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetLabelRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListLabelsRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetMessageRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListMessagesRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ModifyMessageRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class GetThreadRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class ListThreadsRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
  case class WatchRequestMsg(request: () => Future[_], client: Option[ActorRef] = None)
}

class GmailThrottlerClient @Inject()(@Named(GmailThrottlerActor.actorName) googleClientThrottlerActor: ActorRef) {
  implicit val timeout = Timeout(1 minute)

  import scala.concurrent.ExecutionContext.Implicits.global

  def schedule[T](request: GmailRequest[T], msgBuilder: (() => Future[_], Option[ActorRef]) => Any): Future[T] = {
    def execute[T](request: => GmailRequest[T]) = () => Future {concurrent.blocking {request.execute}}
    (googleClientThrottlerActor ? msgBuilder(execute(request), None)).asInstanceOf[Future[T]]
  }

  def scheduleCreateLabel(request: Gmail#Users#Labels#Create) = schedule(request, CreateLabelRequestMsg.apply)
  def scheduleGetLabel(request: Gmail#Users#Labels#Get) = schedule(request, GetLabelRequestMsg.apply)
  def scheduleListLabels(request: Gmail#Users#Labels#List) = schedule(request, ListLabelsRequestMsg.apply)
  def scheduleGetMessage(request: Gmail#Users#Messages#Get) = schedule(request, GetMessageRequestMsg.apply)
  def scheduleListMessages(request: Gmail#Users#Messages#List) = schedule(request, ListMessagesRequestMsg.apply)
  def scheduleModifyMessage(request: Gmail#Users#Messages#Modify) = schedule(request, GetMessageRequestMsg.apply)
  def scheduleGetThread(request: Gmail#Users#Threads#Get) = schedule(request, GetThreadRequestMsg.apply)
  def scheduleListThreads(request: Gmail#Users#Threads#List) = schedule(request, ListThreadsRequestMsg.apply)
  def scheduleWatch(request: Gmail#Users#Watch) = schedule(request, WatchRequestMsg.apply)
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
    case request@CreateLabelRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetLabelRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListLabelsRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetMessageRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListMessagesRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ModifyMessageRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@GetThreadRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@ListThreadsRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
    case request@WatchRequestMsg(_, _) => dispatcher ! request.copy(client = Some(sender))
  }
}

class DispatcherActor extends Actor with ActorLogging {
  // https://developers.google.com/gmail/api/v1/reference/quota
  val createLabelActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val getLabelActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 1 msgsPerSecond)
  val listLabelsActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 1 msgsPerSecond)
  val getMessageActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val listMessagesActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val modifyMessagesActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 5 msgsPerSecond)
  val getThreadActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 10 msgsPerSecond)
  val listThreadsActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 10 msgsPerSecond)
  val watchActor = ThrottlerUtils.createThrottler(context, Props(new SchedulerActor), 100 msgsPerSecond)

  override def receive: Receive = {
    case request@CreateLabelRequestMsg(_, _) => createLabelActor ! request
    case request@GetLabelRequestMsg(_, _) => getLabelActor ! request
    case request@ListLabelsRequestMsg(_, _) => listLabelsActor ! request
    case request@GetMessageRequestMsg(_, _) => getMessageActor ! request
    case request@ListMessagesRequestMsg(_, _) => listMessagesActor ! request
    case request@ModifyMessageRequestMsg(_, _) => modifyMessagesActor ! request
    case request@GetThreadRequestMsg(_, _) => getThreadActor ! request
    case request@ListThreadsRequestMsg(_, _) => listThreadsActor ! request
    case request@WatchRequestMsg(_, _) => watchActor ! request
  }
}

class SchedulerActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case CreateLabelRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetLabelRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListLabelsRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetMessageRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListMessagesRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ModifyMessageRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case GetThreadRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case ListThreadsRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
    case WatchRequestMsg(request, Some(client)) => context.actorOf(Props(new RequestExecutor(request, client)))
  }
}

class RequestExecutor(request: () => Future[_], client: ActorRef) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher
  pipe(request()) to self

  override def receive: Receive = {
    case Status.Failure(f) =>
      client ! Status.Failure(f)
      context.stop(self)
    case response =>
      client ! response
      context.stop(self)
  }
}
