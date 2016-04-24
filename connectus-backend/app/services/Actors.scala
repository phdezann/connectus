package services

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{Actor, ActorLogging, ActorRef, _}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Provider
import com.google.inject.assistedinject.Assisted
import common._
import model.{AttachmentRequest, Contact, OutboxMessage, Resident}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport
import services.JobQueueActor.Job
import services.Repository.AuthorizationCodes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object Timeouts {
  val oneMinute = Timeout(1 minute)
}

@Singleton
class UserActorClient @Inject()(@Named(UserActors.actorName) userActorProvider: Provider[ActorRef]) {
  val userActor = userActorProvider.get
  implicit val timeout = Timeouts.oneMinute
  def getJobQueueActor(email: Email): Future[ActorRef] =
    (userActor ? UserActors.GetJobQueueActor(email)).mapTo[ActorRef]
  def getGmailThrottlerActor(email: Email): Future[Option[ActorRef]] =
    (userActor ? UserActors.GetGmailThrottlerActorActor(email)).mapTo[Option[ActorRef]]
}

@Singleton
class ActorsInitializer @Inject()(environmentHelper: EnvironmentHelper, @Named(UserActors.actorName) userActor: ActorRef) {
  if (!environmentHelper.isInTest) {
    userActor ! UserActors.Setup
  }
}

object UserActors {
  final val actorName = "userActor"
  case object Setup
  case class TradeRequest(authorizationCodes: AuthorizationCodes)
  case class UserAdded(email: Email)
  case class UserRemoved(email: Email)
  case class GetJobQueueActor(email: Email)
  case class GetGmailThrottlerActorActor(email: Email)
  case class UserActorRefs(residentListenerActor: ActorRef, contactListenerActor: ActorRef, gmailWatcherActor: ActorRef, jobQueueActor: ActorRef, gmailThrottlerActor: ActorRef, outboxListenerActor: ActorRef, attachmentListenerActor: ActorRef)
}

class UserActors @Inject()(residentListenerActorFactory: ResidentListenerActor.Factory,
                           contactListenerActorFactory: ContactListenerActor.Factory,
                           outboxListenerActorFactory: OutboxListenerActor.Factory,
                           attachmentListenerActorFactory: AttachmentListenerActor.Factory,
                           @Named(GmailWatcherActor.actorName) gmailWatcherActorProvider: Provider[ActorRef],
                           @Named(JobQueueActor.actorName) jobQueueActorProvider: Provider[ActorRef],
                           @Named(GmailThrottlerActor.actorName) gmailThrottlerActorProvider: Provider[ActorRef],
                           @Named(OutboxActor.actorName) outboxActorProvider: Provider[ActorRef],
                           @Named(AttachmentActor.actorName) attachmentActorProvider: Provider[ActorRef],
                           jobQueueActorClient: JobQueueActorClient,
                           repository: Repository,
                           messageService: MessageService,
                           accountInitializer: AccountInitializer,
                           repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  var jobQueues = Map[Email, UserActors.UserActorRefs]()

  override def receive: Receive = {
    case UserActors.Setup =>
      repository.connect.onComplete {
        case Success(_) => Logger.info("Successfully authenticated to the Firebase database with a JwtToken")
        case Failure(e) => Logger.error("Authentication with a JwtToken to the Firebase database failed", e)
      }
      repositoryListeners.listenForAuthorizationCodes(authorizationCodes => self ! UserActors.TradeRequest(authorizationCodes))
      repositoryListeners.listenForUsers(email => self ! UserActors.UserAdded(email), email => self ! UserActors.UserRemoved(email))
    case UserActors.TradeRequest(authorizationCodes) =>
      def notTradedYet = authorizationCodes.tradeCode.isEmpty
      if (notTradedYet) {
        accountInitializer.addUser(authorizationCodes).map(UserActors.UserAdded(_))
      }
    case UserActors.UserAdded(email: Email) =>
      Logger.info(s"Added user $email")
      val jobQueueActor = jobQueueActorProvider.get
      val gmailWatcherActor = gmailWatcherActorProvider.get
      val residentListenerActor = injectedChild(residentListenerActorFactory(email, jobQueueActor), s"${ResidentListenerActor.actorName}-$email")
      val contactListenerActor = injectedChild(contactListenerActorFactory(email), s"${ContactListenerActor.actorName}-$email")
      val outboxListenerActor = injectedChild(outboxListenerActorFactory(email), s"${OutboxListenerActor.actorName}-$email")
      val attachmentListenerActor = injectedChild(attachmentListenerActorFactory(email), s"${AttachmentListenerActor.actorName}-$email")
      gmailWatcherActor ! GmailWatcherActor.StartWatch(email)
      jobQueueActorClient.schedule(email, messageService.tagInbox(email))
        .onSuccess { case result => Logger.info(s"Result of tagging inbox after new user setup $result") }
      val gmailThrottlerActor = gmailThrottlerActorProvider.get
      jobQueues = jobQueues + ((email, UserActors.UserActorRefs(residentListenerActor, contactListenerActor, gmailWatcherActor, jobQueueActor, gmailThrottlerActor, outboxListenerActor, attachmentListenerActor)))
    case UserActors.UserRemoved(email: Email) =>
      Logger.info(s"Removed user $email")
      val userActors = jobQueues(email)
      jobQueues = jobQueues - email
      userActors.residentListenerActor ! ResidentListenerActor.Stop
      userActors.contactListenerActor ! ContactListenerActor.Stop
      userActors.gmailWatcherActor ! GmailWatcherActor.StopWatch(email)
      userActors.outboxListenerActor ! OutboxListenerActor.Stop
      userActors.attachmentListenerActor ! AttachmentListenerActor.Stop
      context.stop(userActors.jobQueueActor)
    case UserActors.GetJobQueueActor(email: Email) =>
      sender ! jobQueues(email).jobQueueActor
    case UserActors.GetGmailThrottlerActorActor(email: Email) =>
      sender ! jobQueues.get(email).map(_.gmailThrottlerActor)
  }
}


object ResidentListenerActor {
  final val actorName = "residentListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email, jobQueueActor: ActorRef): Actor
  }
}

class ResidentListenerActor @Inject()(@Assisted email: Email, @Assisted jobQueueActor: ActorRef, @Named(ResidentActor.actorName) residentActor: ActorRef, repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  val listener = repositoryListeners.listenForResidents(email, //
    resident => residentActor ! ResidentActor.ResidentAdded(email, resident), //
    resident => residentActor ! ResidentActor.ResidentRemoved(email, resident))

  override def receive: Receive = {
    case ResidentListenerActor.Stop =>
      listener.cancel
      context.stop(self)
  }
}

object ResidentActor {
  final val actorName = "residentActor"
  case class ResidentAdded(email: Email, resident: Resident)
  case class ResidentRemoved(email: Email, resident: Resident)
}

class ResidentActor @Inject()(tagger: LabelService, jobQueueActorClient: JobQueueActorClient) extends Actor with ActorLogging {

  def sync(email: Email) =
    for {
      allLabels <- tagger.listAllLabels(email)
      result <- tagger.syncResidentLabels(email, allLabels)
    } yield result

  def receive: Receive = {
    case request@ResidentActor.ResidentAdded(email, resident) =>
      jobQueueActorClient.schedule(email, sync(email)).map(result => self !(request, result))
    case request@ResidentActor.ResidentRemoved(email, resident) =>
      jobQueueActorClient.schedule(email, sync(email)).map(result => self !(request, result))
    case (ResidentActor.ResidentAdded(email, resident), Success(_)) =>
      Logger.info(s"Success at initializing resident ${resident} for $email")
    case (ResidentActor.ResidentAdded(email, resident), Failure(e)) =>
      Logger.error(s"Failed to initialize resident ${resident} for $email", e)
    case (ResidentActor.ResidentRemoved(email, resident), Success(_)) =>
      Logger.info(s"Success at removing resident ${resident} for $email")
    case (ResidentActor.ResidentRemoved(email, resident), Failure(e)) =>
      Logger.info(s"Failed to remove resident ${resident} for $email", e)
  }
}

object ContactListenerActor {
  final val actorName = "contactListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email): Actor
  }
}

class ContactListenerActor @Inject()(@Assisted email: Email, @Named(ContactActor.actorName) contactActor: ActorRef, repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  val listener = repositoryListeners.listenForContacts(email, contacts => contactActor ! ContactActor.AllContacts(email, contacts))

  override def receive: Receive = {
    case ContactListenerActor.Stop =>
      listener.cancel
      context.stop(self)
  }
}

object ContactActor {
  final val actorName = "contactActor"
  case class AllContacts(email: Email, contacts: List[Contact])
}

class ContactActor @Inject()(messageService: MessageService, jobQueueActorClient: JobQueueActorClient) extends JobQueueActor {
  override def receive: Receive = {
    case ContactActor.AllContacts(email, contacts) =>
      jobQueueActorClient.schedule(email, messageService.tagInbox(email, true), Some("contact"))
        .onSuccess { case result => Logger.info(s"Result of tagging inbox after contact modification $result") }
  }
}


object OutboxListenerActor {
  final val actorName = "outboxListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email): Actor
  }
}

class OutboxListenerActor @Inject()(@Assisted email: Email, @Named(OutboxActor.actorName) outboxActor: ActorRef, repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  val listener = repositoryListeners.listenForOutboxMessages(email, outboxMessage => outboxActor ! OutboxActor.OutboxMessageAdded(email, outboxMessage))

  override def receive: Receive = {
    case OutboxListenerActor.Stop =>
      listener.cancel
      context.stop(self)
  }
}

object OutboxActor {
  final val actorName = "outboxActor"
  case class OutboxMessageAdded(email: Email, outboxMessage: OutboxMessage)
}

class OutboxActor @Inject()(messageService: MessageService, jobQueueActorClient: JobQueueActorClient) extends JobQueueActor {
  override def receive: Receive = {
    case OutboxActor.OutboxMessageAdded(email, outboxMessage) =>
      jobQueueActorClient.schedule(email, messageService.reply(email, outboxMessage))
        .onSuccess { case result => Logger.info(s"Result of replying $result") }
  }
}

object AttachmentListenerActor {
  final val actorName = "attachmentListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email): Actor
  }
}

class AttachmentListenerActor @Inject()(@Assisted email: Email, @Named(AttachmentActor.actorName) attachmentActor: ActorRef, repositoryListeners: RepositoryListeners) extends Actor with ActorLogging with InjectedActorSupport {

  val listener = repositoryListeners.listenForAttachmentRequests(email, attachmentRequest => attachmentActor ! AttachmentActor.AttachmentRequestAdded(email, attachmentRequest))

  override def receive: Receive = {
    case AttachmentListenerActor.Stop =>
      listener.cancel
      context.stop(self)
  }
}

object AttachmentActor {
  final val actorName = "attachmentActor"
  case class AttachmentRequestAdded(email: Email, attachmentRequest: AttachmentRequest)
}

class AttachmentActor @Inject()(messageService: MessageService, jobQueueActorClient: JobQueueActorClient) extends JobQueueActor {
  override def receive: Receive = {
    case AttachmentActor.AttachmentRequestAdded(email, attachmentRequest) =>
      messageService.prepareRequest(email, attachmentRequest)
        .onSuccess { case result => Logger.info(s"Result of attachment request $result") }
  }
}

class JobQueueActorClient @Inject()(userActorClient: UserActorClient) {
  implicit val timeout = Timeouts.oneMinute

  def schedule(email: Email, job: => Future[_], key: Option[String] = None): Future[Try[_]] =
    userActorClient.getJobQueueActor(email)
      .flatMap(_ ? Job(() => job, key)).mapTo[Try[_]]
}
