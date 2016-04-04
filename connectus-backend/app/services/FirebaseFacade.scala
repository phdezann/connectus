package services

import java.util
import javax.inject.{Inject, Singleton}

import _root_.support.AppConf
import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client._
import common._
import model.{Contact, GmailLabel, Resident}
import play.api.Logger
import services.FirebaseFacade._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

// to transfer to the repository
object FirebaseFacade {
  val AuthorizationCodesPath = "authorization_codes"
  val AndroidIdPath = "android_id"
  val AuthorizationCodePath = "authorization_code"
  val TradeLogPath = "trade_log"
  val CodePath = "code"
  val MessagePath = "message"
  val RefreshTokenPath = "refresh_token"
  val AccessTokenPath = "access_token"
  val ExpirationTimeMilliSecondsPath = "expiration_time_milli_seconds"
  val UsersPath = "users"
  val ResidentsPath = "residents"
  val ContactsPath = "contacts"
  val ResidentIdProperty = "id"
  val ResidentNameProperty = "name"
  val ResidentLabelNameProperty = "labelName"
  val ResidentLabelIdProperty = "labelId"

  val LoginCodeSuccess = "SUCCESS"
  val LoginCodeInvalidGrant = "INVALID_GRANT"
  val LoginCodeFailure = "FAILURE"

  case class AuthorizationCodes(authorizationCodeId: String, androidId: String, authorizationCode: String, tradeCode: Option[String])
  case class UserCredential(refreshToken: String, accessToken: String, expirationTimeInMilliSeconds: Long)
  case class MessagesSnapshot(allThreadIds: Map[ThreadId, List[MessageId]] = Map(), messagesLabels: Map[MessageId, List[GmailLabel]] = Map())
}

@Singleton
class FirebaseFacade @Inject()(appConf: AppConf) {

  def connect = {
    new Firebase(appConf.getFirebaseUrl)
      .authWithCustomToken(appConf.getFirebaseJwtToken, new AuthResultHandler {
        override def onAuthenticated(authData: AuthData) = Logger.info("Successfully authenticated to the Firebase database with a JwtToken")
        override def onAuthenticationError(firebaseError: FirebaseError) = Logger.error("Authentication with a JwtToken to the Firebase database failed", firebaseError.toException)
      })
  }

  // TODO: refactor this.
  def listenChildEvent(url: String, onChildAddedCallback: DataSnapshot => Unit, onChildRemovedCallback: DataSnapshot => Unit = _ => (), onChildChangedCallback: DataSnapshot => Unit = _ => ()): Cancellable = {
    val ref = new Firebase(url)

    val listener: ChildEventListener = ref.addChildEventListener(new ChildEventListener {
      override def onChildAdded(snapshot: DataSnapshot, previousChildName: String) = onChildAddedCallback(snapshot)
      override def onChildRemoved(snapshot: DataSnapshot) = onChildRemovedCallback(snapshot)
      override def onChildMoved(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onChildChanged(snapshot: DataSnapshot, previousChildName: String) = onChildChangedCallback(snapshot)
      override def onCancelled(error: FirebaseError) = {}
    })

    new Cancellable {
      def cancel = {
        ref.removeEventListener(listener)
      }
    }
  }

  def updateAccessToken(email: Email, accessToken: Option[String], expirationTimeInMilliSeconds: Option[Long]): Future[Unit] = {
    val encodedEmail = Util.encode(email)
    val values: Map[String, AnyRef] = Map(
      s"$UsersPath/$encodedEmail/$AccessTokenPath" -> accessToken.fold[String](null)(identity),
      s"$UsersPath/$encodedEmail/$ExpirationTimeMilliSecondsPath" -> expirationTimeInMilliSeconds.fold[java.lang.Long](null)(Long.box(_)))
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  def getCredentials(email: Email): Future[UserCredential] = {
    val encodedEmail = Util.encode(email)
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/$UsersPath/$encodedEmail")
    FutureWrappers.getValueFuture(ref).map { dataSnapshot =>
      val refreshToken = dataSnapshot.child(RefreshTokenPath).getValue.asInstanceOf[String]
      val accessToken = dataSnapshot.child(AccessTokenPath).getValue.asInstanceOf[String]
      val expirationTimeInMilliSeconds = Long.unbox(dataSnapshot.child(ExpirationTimeMilliSecondsPath).getValue)
      UserCredential(refreshToken, accessToken, expirationTimeInMilliSeconds)
    }
  }

  def getMessagesSnapshot(email: Email): Future[MessagesSnapshot] = {
    def toChildrenList(snapshot: DataSnapshot) = snapshot.getChildren.iterator().asScala.toList
    val encodedEmail = Util.encode(email)
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/messages/$encodedEmail/admin/threads")
    FutureWrappers.getValueFuture(ref).map(snapshot => {
      val messages = toChildrenList(snapshot)
      val threadsPairs = messages.flatMap(thread => {
        toChildrenList(thread).map(message => {
          val threadId = thread.getKey
          val messageId = message.getKey
          (threadId, messageId)
        })
      })
      val labelsPair = messages.flatMap(thread => {
        toChildrenList(thread).map(message => {
          val messageId = message.getKey
          val labels = toChildrenList(message.child("labels")).map(label => GmailLabel(label.getKey, label.getValue.asInstanceOf[String]))
          (messageId, labels)
        })
      })
      val threads = threadsPairs.groupBy(_._1).mapValues(_.map(_._2))
      val labels = labelsPair.toMap
      MessagesSnapshot(threads, labels)
    })
  }

  def saveMessages(values: Map[String, AnyRef]): Future[Unit] = {
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  def addResidentLabelId(email: Email, residentId: String, labelId: String): Future[Unit] = {
    val residentsRef = new Firebase(s"${appConf.getFirebaseUrl}/$ResidentsPath/${Util.encode(email)}/$residentId/$ResidentLabelIdProperty")
    FutureWrappers.setValueFuture(residentsRef, labelId)
  }

  def getResidentsAndContacts(email: Email): Future[Map[Resident, List[Contact]]] = {
    def asResidents(snapshot: DataSnapshot): List[Resident] =
      snapshot.getChildren.asScala.toList.map { snapshot =>
        val id = snapshot.getKey
        val name = snapshot.child(ResidentNameProperty).getValue.asInstanceOf[String]
        val labelName = snapshot.child(ResidentLabelNameProperty).getValue.asInstanceOf[String]
        val labelIdOpt = Option(snapshot.child(ResidentLabelIdProperty).getValue.asInstanceOf[String])
        Resident(id, name, labelName, labelIdOpt)
      }

    def asContacts(snapshot: DataSnapshot): List[Contact] = {
      snapshot.getChildren.asScala.toList.flatMap { residentSnapshot =>
        val residentId = residentSnapshot.getKey
        val contacts: List[DataSnapshot] = residentSnapshot.getChildren.asScala.toList
        contacts.map { contact =>
          val email = Util.decode(contact.getKey)
          Contact(email, residentId)
        }
      }
    }
    def merge(residents: List[Resident], contacts: List[Contact]): Map[Resident, List[Contact]] =
      residents.map { resident =>
        val filter: List[Contact] = contacts.filter(contact => contact.residentId == resident.id)
        (resident, filter)
      }.toMap
    val residentsRef = new Firebase(s"${appConf.getFirebaseUrl}/$ResidentsPath/${Util.encode(email)}")
    val contactsRef = new Firebase(s"${appConf.getFirebaseUrl}/$ContactsPath/${Util.encode(email)}")
    val residents: Future[List[Resident]] = FutureWrappers.getValueFuture(residentsRef).map(asResidents(_))
    val contacts: Future[List[Contact]] = FutureWrappers.getValueFuture(contactsRef).map(asContacts(_))
    residents.zip(contacts).map(ee => merge(ee._1, ee._2))
  }
}

object Util {
  def encode(email: Email) = email.replace('.', ',')
  def decode(email: Email) = email.replace(',', '.')
}

object FutureWrappers {
  def updateChildrenFuture(firebase: Firebase, values: util.Map[String, AnyRef]) = {
    val promise = Promise[Unit]
    firebase.updateChildren(values, new CompletionListener {
      override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
        if (firebaseError == null) {
          promise.success(())
        } else {
          promise.failure(firebaseError.toException)
        }
      }
    })
    promise.future
  }

  def setValueFuture(firebase: Firebase, value: AnyRef) = {
    val promise = Promise[Unit]
    firebase.setValue(value, new CompletionListener {
      override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
        if (firebaseError == null) {
          promise.success(())
        } else {
          promise.failure(firebaseError.toException)
        }
      }
    })
    promise.future
  }

  def getValueFuture(firebase: Firebase) = {
    val promise = Promise[DataSnapshot]
    firebase.addListenerForSingleValueEvent(new ValueEventListener() {
      override def onDataChange(dataSnapshot: DataSnapshot) = promise.success(dataSnapshot)
      override def onCancelled(firebaseError: FirebaseError) = promise.failure(firebaseError.toException)
    })
    promise.future
  }
}

