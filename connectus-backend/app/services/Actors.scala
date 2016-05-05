package services

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, _}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Provider
import com.google.inject.assistedinject.Assisted
import common._
import conf.AppConf
import model.{AttachmentRequest, Contact, OutboxMessage, Resident}
import play.api.Logger
import play.api.inject.Injector
import play.api.libs.concurrent.InjectedActorSupport
import services.HistoryIdHolderActor.SetHistoryId
import services.JobQueueActor.Job
import services.Repository.AuthorizationCodes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object Timeouts {
  val oneMinute = Timeout(1 minute)
}

@Singleton
class ActorsClient @Inject()(appConf: AppConf, @Named(SuperSupervisorActor.actorName) superSupervisorActorProvider: Provider[ActorRef]) {

  var superSupervisorActor: ActorRef = _
  implicit val timeout = Timeouts.oneMinute

  if (!appConf.getMaintenanceMode) {
    superSupervisorActor = superSupervisorActorProvider.get
  }

  def scheduleOnUserJobQueue(email: Email, job: => Future[_], key: Option[String] = None): Future[Try[_]] = getJobQueueActor(email)
    .flatMap(_ ? Job(() => job, key)).mapTo[Try[_]]

  def getGmailThrottlerActor(email: Email): Future[ActorRef] =
    (superSupervisorActor ? UserSupervisorActor.GetGmailThrottlerActor(email)).mapTo[ActorRef]

  def getJobQueueActor(email: Email): Future[ActorRef] =
    (superSupervisorActor ? UserSupervisorActor.GetJobQueueActor(email)).mapTo[ActorRef]

  def getHistoryId(email: Email): Future[Option[BigInt]] =
    (superSupervisorActor ? HistoryIdHolderActor.GetHistoryId(email)).mapTo[HistoryIdHolderActor.HistoryIdValue].map(_.historyId)

  def setHistoryId(email: Email, historyId: Option[BigInt]): Future[Unit] =
    (superSupervisorActor ? HistoryIdHolderActor.SetHistoryId(email, historyId)).mapTo[HistoryIdHolderActor.HistoryIdValue].map(_ => ())
}

object SuperSupervisorActor {
  final val actorName = "superSupervisorActor"
  case class TradeRequest(authorizationCodes: AuthorizationCodes)
  case class UserAdded(email: Email)
  case class UserRemoved(email: Email)
}

class SuperSupervisorActor @Inject()(userSupervisorActorFactory: UserSupervisorActor.Factory,
                                     repository: Repository,
                                     environmentHelper: EnvironmentHelper,
                                     accountInitializer: AccountInitializer,
                                     repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  if (!environmentHelper.isInTest) {
    repository.connect.onComplete {
      case Success(_) => Logger.info("Successfully authenticated to the Firebase database with a JwtToken")
      case Failure(e) => Logger.error("Authentication with a JwtToken to the Firebase database failed", e)
    }
    repositoryListeners.listenForAuthorizationCodes(
      authorizationCodes => self ! SuperSupervisorActor.TradeRequest(authorizationCodes))
    repositoryListeners.listenForUsers(
      email => self ! SuperSupervisorActor.UserAdded(email), email => self ! SuperSupervisorActor.UserRemoved(email))
  }

  override def receive: Receive = {
    case SuperSupervisorActor.TradeRequest(authorizationCodes) =>
      def notTradedYet = authorizationCodes.tradeCode.isEmpty
      if (notTradedYet) {
        accountInitializer.addUser(authorizationCodes).map(SuperSupervisorActor.UserAdded(_))
      }
    case SuperSupervisorActor.UserAdded(email) =>
      injectedChild(userSupervisorActorFactory.apply(email), email)
    case SuperSupervisorActor.UserRemoved(email) =>
      context.stop(context.child(email).get)
    case msg@UserSupervisorActor.GetJobQueueActor(email) =>
      context.child(email).get forward msg
    case msg@UserSupervisorActor.GetGmailThrottlerActor(email) =>
      context.child(email).get forward msg
    case msg@HistoryIdHolderActor.GetHistoryId(email) =>
      context.child(email).get forward msg
    case msg@HistoryIdHolderActor.SetHistoryId(email, _) =>
      context.child(email).get forward msg
  }
}

object UserSupervisorActor {
  final val actorName = "userSupervisorActor"
  case class GetJobQueueActor(email: Email)
  case class GetGmailThrottlerActor(email: Email)
  trait Factory {
    def apply(email: Email): Actor
  }
}

class UserSupervisorActor @Inject()(@Assisted email: Email,
                                    actorsClient: ActorsClient,
                                    messageService: MessageService,
                                    repositoryListeners: RepositoryListeners,
                                    injector: Injector) extends Actor with ActorLogging {

  val gmailWatcherActorRef = createChildActor[GmailWatcherActor]
  val jobQueueActorRef = createChildActor[JobQueueActor]
  val gmailThrottlerActorRef = createChildActor[GmailThrottlerActor]
  val historyIdHolderActorRef = createChildActor[HistoryIdHolderActor]
  val residentActorRef = createChildActor[ResidentActor]
  val contactActorRef = createChildActor[ContactActor]
  val outboxActorRef = createChildActor[OutboxActor]
  val attachmentActorRef = createChildActor[AttachmentActor]

  val residentListener = repositoryListeners.listenForResidents(email,
    resident => residentActorRef ! ResidentActor.ResidentAdded(email, resident),
    resident => residentActorRef ! ResidentActor.ResidentRemoved(email, resident))
  val contactListener = repositoryListeners.listenForContacts(email,
    contacts => contactActorRef ! ContactActor.AllContacts(email, contacts))
  val outboxListener = repositoryListeners.listenForOutboxMessages(email,
    outboxMessage => outboxActorRef ! OutboxActor.OutboxMessageAdded(email, outboxMessage))
  val attachmentListener = repositoryListeners.listenForAttachmentRequests(email,
    attachmentRequest => attachmentActorRef ! AttachmentActor.AttachmentRequestAdded(email, attachmentRequest))

  gmailWatcherActorRef ! GmailWatcherActor.StartWatch(email)

  actorsClient.scheduleOnUserJobQueue(email, messageService.tagInbox(email))
    .onSuccess { case result => Logger.info(s"Result of tagging inbox after new user setup $result for $email") }

  Logger.info(s"Added user $email")

  override def receive: Receive = {
    case UserSupervisorActor.GetJobQueueActor(_) =>
      sender ! jobQueueActorRef
    case UserSupervisorActor.GetGmailThrottlerActor(_) =>
      sender ! gmailThrottlerActorRef
    case msg@HistoryIdHolderActor.GetHistoryId(_) =>
      historyIdHolderActorRef forward msg
    case msg@HistoryIdHolderActor.SetHistoryId(_, _) =>
      historyIdHolderActorRef forward msg
  }

  override def postStop(): Unit = {
    residentListener.cancel
    contactListener.cancel
    outboxListener.cancel
    attachmentListener.cancel
    Logger.info(s"Removed user $email")
  }

  private def createChildActor[T <: Actor : ClassTag]: ActorRef =
    context.actorOf(identity(Props(injector.instanceOf[T])))
}

object ResidentActor {
  final val actorName = "residentActor"
  case class ResidentAdded(email: Email, resident: Resident)
  case class ResidentRemoved(email: Email, resident: Resident)
}

class ResidentActor @Inject()(tagger: LabelService, actorsClient: ActorsClient) extends Actor with ActorLogging {

  def sync(email: Email) =
    for {
      allLabels <- tagger.listAllLabels(email)
      result <- tagger.syncResidentLabels(email, allLabels)
    } yield result

  def receive: Receive = {
    case request@ResidentActor.ResidentAdded(email, resident) =>
      actorsClient.scheduleOnUserJobQueue(email, sync(email))
        .onSuccess { case result => Logger.info(s"Result of initializing resident $result") }
    case request@ResidentActor.ResidentRemoved(email, resident) =>
      actorsClient.scheduleOnUserJobQueue(email, sync(email))
        .onSuccess { case result => Logger.info(s"Result of removing resident $result") }
  }
}

object ContactActor {
  final val actorName = "contactActor"
  case class AllContacts(email: Email, contacts: List[Contact])
}

class ContactActor @Inject()(messageService: MessageService, actorsClient: ActorsClient) extends Actor with ActorLogging {
  override def receive: Receive = {
    case ContactActor.AllContacts(email, contacts) =>
      Logger.info(s"AllContacts msg received in contact actor for $email")
      actorsClient.scheduleOnUserJobQueue(email, messageService.tagInbox(email), Some("contact"))
        .onSuccess { case result => Logger.info(s"Result of tagging inbox after contact modification $result for $email") }
  }
}

object OutboxActor {
  final val actorName = "outboxActor"
  case class OutboxMessageAdded(email: Email, outboxMessage: OutboxMessage)
}

class OutboxActor @Inject()(messageService: MessageService, actorsClient: ActorsClient) extends Actor with ActorLogging {
  override def receive: Receive = {
    case OutboxActor.OutboxMessageAdded(email, outboxMessage) =>
      actorsClient.scheduleOnUserJobQueue(email, messageService.reply(email, outboxMessage))
        .onSuccess { case result => Logger.info(s"Result of replying $result") }
  }
}

object AttachmentActor {
  final val actorName = "attachmentActor"
  case class AttachmentRequestAdded(email: Email, attachmentRequest: AttachmentRequest)
}

class AttachmentActor @Inject()(messageService: MessageService) extends Actor with ActorLogging {
  override def receive: Receive = {
    case AttachmentActor.AttachmentRequestAdded(email, attachmentRequest) =>
      messageService.prepareRequest(email, attachmentRequest)
        .onSuccess { case result => Logger.info(s"Result of attachment request $result") }
  }
}

object HistoryIdHolderActor {
  final val actorName = "historyIdHolderActor"
  case class GetHistoryId(email: Email)
  case class SetHistoryId(email: Email, newHistoryId: Option[BigInt])
  case class HistoryIdValue(historyId: Option[BigInt])
}

class HistoryIdHolderActor extends Actor with ActorLogging {

  var historyId: Option[BigInt] = None

  override def receive: Receive = {
    case HistoryIdHolderActor.GetHistoryId(_) =>
      sender ! HistoryIdHolderActor.HistoryIdValue(historyId)
    case SetHistoryId(_, newHistoryId) =>
      historyId = newHistoryId
      sender ! HistoryIdHolderActor.HistoryIdValue(historyId)
  }
}
