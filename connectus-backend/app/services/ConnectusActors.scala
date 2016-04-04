package services

import javax.inject.{Inject, Named}

import _root_.support.AppConf
import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, ActorRef, _}
import akka.pattern.ask
import akka.util.Timeout
import com.firebase.client.{DataSnapshot, Firebase}
import com.google.inject.Provider
import com.google.inject.assistedinject.Assisted
import common._
import model.{Contact, Resident}
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport
import services.ContactActor.AllContacts
import services.FirebaseFacade.AuthorizationCodes
import services.JobQueueActor.{Job, JobResult}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class JobQueueActorClient @Inject()(@Named(UserActor.actorName) userActorProvider: Provider[ActorRef]) {
  val userActor = userActorProvider.get

  implicit val timeout = Timeout(5 minutes)
  def schedule(email: Email, job: => Future[_]) =
    (userActor ? UserActor.GetJobQueueActor(email)).mapTo[ActorRef]
      .flatMap(_ ? Job(() => job)).mapTo[JobResult]
      .map { case JobResult(Success(result), _, _) => result }
}

class UserActorInitializer @Inject()(@Named(UserActor.actorName) userActor: ActorRef) {
  userActor ! UserActor.Setup
}

object UserActor {
  final val actorName = "userActor"
  case object Setup
  case class TradeRequest(authorizationCodes: AuthorizationCodes)
  case class UserAdded(email: Email)
  case class UserRemoved(email: Email)
  case class GetJobQueueActor(email: Email)

  case class UserActors(residentListenerActor: ActorRef, contactListenerActor: ActorRef, gmailWatcherActor: ActorRef, jobQueueActor: ActorRef)
}

class UserActor @Inject()(residentListenerActorFactory: ResidentListenerActor.Factory,
                          contactListenerActorFactory: ContactListenerActor.Factory,
                          @Named(GmailWatcherActor.actorName) gmailWatcherActorProvider: Provider[ActorRef],
                          @Named(JobQueueActor.actorName) jobQueueActorProvider: Provider[ActorRef],
                          firebaseFacade: FirebaseFacade,
                          messageService: MessageService,
                          accountInitializer: AccountInitializer,
                          watcher: Watcher) extends Actor with ActorLogging with InjectedActorSupport {

  var jobQueues = Map[Email, UserActor.UserActors]()

  override def receive: Receive = {
    case UserActor.Setup =>
      firebaseFacade.connect
      watcher.listenForAuthorizationCodes(authorizationCodes => self ! UserActor.TradeRequest(authorizationCodes))
      watcher.listenForUsers(email => self ! UserActor.UserAdded(email), email => self ! UserActor.UserRemoved(email))
    case UserActor.TradeRequest(authorizationCodes) =>
      def notTradedYet = authorizationCodes.tradeCode.isEmpty
      if (notTradedYet) {
        accountInitializer.addUser(authorizationCodes).map(UserActor.UserAdded(_))
      }
    case UserActor.UserAdded(email: Email) =>
      Logger.info(s"Added user $email")
      val jobQueueActor = jobQueueActorProvider.get
      val gmailWatcherActor = gmailWatcherActorProvider.get
      val residentListenerActor = injectedChild(residentListenerActorFactory(email, jobQueueActor), s"${ResidentListenerActor.actorName}-$email")
      val contactListenerActor = injectedChild(contactListenerActorFactory(email, jobQueueActor), s"${ContactListenerActor.actorName}-$email")
      gmailWatcherActor ! GmailWatcherActor.StartWatch(email)
      jobQueueActor ! Job(() => messageService.tagInbox(email))
      jobQueues = jobQueues + ((email, UserActor.UserActors(residentListenerActor, contactListenerActor, gmailWatcherActor, jobQueueActor)))
    case UserActor.UserRemoved(email: Email) =>
      Logger.info(s"Removed user $email")
      val userActors = jobQueues(email)
      jobQueues = jobQueues - email
      userActors.residentListenerActor ! ResidentListenerActor.Stop
      userActors.contactListenerActor ! ContactListenerActor.Stop
      userActors.gmailWatcherActor ! GmailWatcherActor.StopWatch(email)
      context.stop(userActors.jobQueueActor)
    case UserActor.GetJobQueueActor(email: Email) =>
      sender ! jobQueues(email).jobQueueActor
  }
}


object ResidentListenerActor {
  final val actorName = "residentListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email, jobQueueActor: ActorRef): Actor
  }
}

class ResidentListenerActor @Inject()(@Assisted email: Email, @Assisted jobQueueActor: ActorRef, residentActorFactory: ResidentActor.Factory, watcher: Watcher) extends Actor with ActorLogging with InjectedActorSupport {

  val residentActor = injectedChild(residentActorFactory(jobQueueActor), s"${ResidentActor.actorName}-$email")

  val listener = watcher.listenForResidents(email, //
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
  case class ResidentAddedSuccess(email: Email, resident: Resident)
  case class ResidentAddedFailure(email: Email, resident: Resident, e: Throwable)
  case class ResidentRemovedSuccess(email: Email, resident: Resident)
  case class ResidentRemovedFailure(email: Email, resident: Resident, e: Throwable)

  trait Factory {
    def apply(jobQueueActor: ActorRef): Actor
  }
}

class ResidentActor @Inject()(watcher: Watcher, tagger: Tagger, @Assisted jobQueueActor: ActorRef) extends Actor with ActorLogging {
  def receive: Receive = {
    case ResidentActor.ResidentAdded(email, resident) =>
      val action = tagger.createTag(email, resident)
        .map(_ => ResidentActor.ResidentAddedSuccess(email, resident))
        .recover { case e => ResidentActor.ResidentAddedFailure(email, resident, e) }
      jobQueueActor ! Job(() => action)
    case ResidentActor.ResidentRemoved(email, resident) =>
      val action = tagger.deleteTag(email, resident)
        .map(_ => ResidentActor.ResidentRemovedSuccess(email, resident))
        .recover { case e => ResidentActor.ResidentRemovedFailure(email, resident, e) }
      jobQueueActor ! Job(() => action)
    case ResidentActor.ResidentAddedSuccess(email, resident) =>
      Logger.info(s"Success at initializing resident ${resident.labelId} for $email")
    case ResidentActor.ResidentAddedFailure(email, resident, e) =>
      Logger.info(s"Failed to initialize resident ${resident.labelId} for $email")
    case ResidentActor.ResidentRemovedSuccess(email, resident) =>
      Logger.info(s"Success at removing resident ${resident.labelId} for $email")
    case ResidentActor.ResidentRemovedFailure(email, resident, e) =>
      Logger.info(s"Failed to removing resident ${resident.labelId} for $email")
  }
}

object ContactListenerActor {
  final val actorName = "contactListenerActor"
  case object Stop

  trait Factory {
    def apply(email: Email, jobQueueActor: ActorRef): Actor
  }
}

class ContactListenerActor @Inject()(@Assisted email: Email, @Assisted jobQueueActor: ActorRef, contactActorFactory: ContactActor.Factory, watcher: Watcher) extends Actor with ActorLogging with InjectedActorSupport {

  val contactActor = injectedChild(contactActorFactory(jobQueueActor), s"${ContactActor.actorName}-$email")
  val listener = watcher.listenForContacts(email, contacts => contactActor ! ContactActor.AllContacts(email, contacts))

  override def receive: Receive = {
    case ContactListenerActor.Stop =>
      listener.cancel
      context.stop(self)
  }
}

object ContactActor {
  final val actorName = "contactActor"
  case class AllContacts(email: Email, contacts: List[Contact])

  trait Factory {
    def apply(jobQueueActor: ActorRef): Actor
  }
}

class ContactActor @Inject()(messageService: MessageService, @Assisted jobQueueActor: ActorRef) extends JobQueueActor {
  override def receive: Receive = {
    case AllContacts(email, contacts) =>
      jobQueueActor ! Job(() => messageService.tagInbox(email))
  }
}

trait Cancellable {
  def cancel: Unit
}

class Watcher @Inject()(appConf: AppConf, firebaseFacade: FirebaseFacade) {

  def listenForUsers(onUserAdded: Email => Unit, onUserRemoved: Email => Unit): Cancellable = {
    def toEmail(snapshot: DataSnapshot): String = Util.decode(snapshot.getKey)
    firebaseFacade.listenChildEvent(s"${appConf.getFirebaseUrl}/${FirebaseFacade.UsersPath}", snapshot => onUserAdded(toEmail(snapshot)), snapshot => onUserRemoved(toEmail(snapshot)))
  }

  def listenForAuthorizationCodes(onAuthorizationCodesAdded: AuthorizationCodes => Unit): Cancellable =
    firebaseFacade.listenChildEvent(s"${appConf.getFirebaseUrl}/${FirebaseFacade.AuthorizationCodesPath}", snapshot => {
      val providedAndroidId = snapshot.child(FirebaseFacade.AndroidIdPath).getValue.asInstanceOf[String]
      val authorizationCode = snapshot.child(FirebaseFacade.AuthorizationCodePath).getValue.asInstanceOf[String]
      val tradeCode = Option(snapshot.child(FirebaseFacade.TradeLogPath).child(FirebaseFacade.CodePath).getValue).asInstanceOf[Option[String]]
      onAuthorizationCodesAdded(AuthorizationCodes(snapshot.getKey, providedAndroidId, authorizationCode, tradeCode))
    })

  def listenForResidents(email: Email, onResidentAdded: Resident => Unit, onResidentRemoved: Resident => Unit): Cancellable =
    firebaseFacade.listenChildEvent(s"${appConf.getFirebaseUrl}/${FirebaseFacade.ResidentsPath}/${Util.encode(email)}", snapshot => {
      val id = snapshot.getKey
      val name = snapshot.child(FirebaseFacade.ResidentNameProperty).getValue.asInstanceOf[String]
      val labelName = snapshot.child(FirebaseFacade.ResidentLabelNameProperty).getValue.asInstanceOf[String]
      val labelIdOpt = Option(snapshot.child(FirebaseFacade.ResidentLabelIdProperty).getValue.asInstanceOf[String])
      onResidentAdded(Resident(id, name, labelName, labelIdOpt))
    })

  def listenForContacts(email: Email, onContactsModified: List[Contact] => Unit): Cancellable = {
    val contactUrl = s"${appConf.getFirebaseUrl}/${FirebaseFacade.ContactsPath}/${Util.encode(email)}"
    def callback: (DataSnapshot) => Unit = {
      snapshot => {
        FutureWrappers.getValueFuture(new Firebase(contactUrl)).map { residentSnapshot =>
          val residentId = residentSnapshot.getKey
          val contacts: List[DataSnapshot] = residentSnapshot.getChildren.asScala.toList
          onContactsModified(contacts.map { contact => Contact(Util.decode(contact.getKey), residentId) })
        }
      }
    }
    firebaseFacade.listenChildEvent(contactUrl, callback, _ => (), callback)
  }
}
