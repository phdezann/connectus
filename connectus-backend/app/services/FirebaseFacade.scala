package services

import java.util
import javax.inject.{Inject, Singleton}

import _root_.support.AppConf
import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client._
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.common.base.Throwables
import common._
import model.{Contact, Resident}
import play.api.Logger

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import FirebaseFacade._

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
}

@Singleton
class FirebaseFacade @Inject()(appConf: AppConf) {

  connect

  def connect = {
    new Firebase(appConf.getFirebaseUrl)
      .authWithCustomToken(appConf.getFirebaseJwtToken, new AuthResultHandler {
        override def onAuthenticated(authData: AuthData) = Logger.info("Successfully authenticated to the Firebase database with a JwtToken")
        override def onAuthenticationError(firebaseError: FirebaseError) = Logger.error("Authentication with a JwtToken to the Firebase database failed", firebaseError.toException)
      })
  }

  case class AuthorizationCode(authorizationCodeId: String, androidId: String, authorizationCode: String, tradeCode: Option[String])
  def listenAuthorizationCodes(listener: AuthorizationCode => Unit) = {
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/$AuthorizationCodesPath")
    ref.addChildEventListener(new ChildEventListener {
      override def onChildAdded(snapshot: DataSnapshot, previousChildName: String) = {
        val providedAndroidId = snapshot.child(AndroidIdPath).getValue.asInstanceOf[String]
        val authorizationCode = snapshot.child(AuthorizationCodePath).getValue.asInstanceOf[String]
        val tradeCode = Option(snapshot.child(TradeLogPath).child(CodePath).getValue).asInstanceOf[Option[String]]
        listener(AuthorizationCode(snapshot.getKey, providedAndroidId, authorizationCode, tradeCode))
      }
      override def onChildRemoved(snapshot: DataSnapshot) = {}
      override def onChildMoved(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onChildChanged(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onCancelled(error: FirebaseError) = {}
    })
  }

  def listenUsers(onAddListener: Email => Unit, onRemovedListener: Email => Unit) = {
    def getUserEmail(snapshot: DataSnapshot) = Util.decode(snapshot.getKey)
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/$UsersPath")
    ref.addChildEventListener(new ChildEventListener {
      override def onChildAdded(snapshot: DataSnapshot, previousChildName: String) = onAddListener(getUserEmail(snapshot))
      override def onChildRemoved(snapshot: DataSnapshot) = onRemovedListener(getUserEmail(snapshot))
      override def onChildMoved(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onChildChanged(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onCancelled(error: FirebaseError) = {}
    })
  }

  def initAccount(authorizationCodeId: String, email: Email, googleTokenResponse: GoogleTokenResponse) = {
    def expirationTimeInMilliSeconds(expiresInSecondsFromNow: Long) = System.currentTimeMillis + expiresInSecondsFromNow * 1000
    val encodedEmail = Util.encode(email)
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$CodePath" -> LoginCodeSuccess,
      s"$UsersPath/$encodedEmail/$RefreshTokenPath" -> googleTokenResponse.getRefreshToken,
      s"$UsersPath/$encodedEmail/$AccessTokenPath" -> googleTokenResponse.getAccessToken,
      s"$UsersPath/$encodedEmail/$ExpirationTimeMilliSecondsPath" -> Long.box(expirationTimeInMilliSeconds(googleTokenResponse.getExpiresInSeconds)))
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  def onTradeFailure(authorizationCodeId: String, e: Throwable) = {
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$CodePath" -> getCode(e),
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$MessagePath" -> Throwables.getStackTraceAsString(e))
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  def getCode(exception: Throwable) = {
    exception match {
      case tre: TokenResponseException if tre.getDetails.getError == "invalid_grant" => LoginCodeInvalidGrant
      case _ => LoginCodeFailure
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

  case class Credential(refreshToken: String, accessToken: String, expirationTimeInMilliSeconds: Long)
  def getCredentials(email: Email): Future[Credential] = {
    val encodedEmail = Util.encode(email)
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/$UsersPath/$encodedEmail")
    FutureWrappers.getValueFuture(ref).map { dataSnapshot =>
      val refreshToken = dataSnapshot.child(RefreshTokenPath).getValue.asInstanceOf[String]
      val accessToken = dataSnapshot.child(AccessTokenPath).getValue.asInstanceOf[String]
      val expirationTimeInMilliSeconds = Long.unbox(dataSnapshot.child(ExpirationTimeMilliSecondsPath).getValue)
      Credential(refreshToken, accessToken, expirationTimeInMilliSeconds)
    }
  }

  def listenContacts(listener: Email => Unit) = {
    def notifyListeners(snapshot: DataSnapshot): Unit = {
      val email = snapshot.getKey
      listener(email)
    }
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/$ContactsPath")
    ref.addChildEventListener(new ChildEventListener {
      override def onChildAdded(snapshot: DataSnapshot, previousChildName: String) = notifyListeners(snapshot)
      override def onChildChanged(snapshot: DataSnapshot, previousChildName: String) = notifyListeners(snapshot)
      override def onChildRemoved(snapshot: DataSnapshot) = {}
      override def onChildMoved(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onCancelled(error: FirebaseError) = {}
    })
  }

  def getAdminThreadIds(email: Email): Future[Map[ThreadId, List[MessageId]]] = {
    def toChildrenList(snapshot: DataSnapshot) = snapshot.getChildren.iterator().asScala.toList
    val encodedEmail = Util.encode(email)
    val ref = new Firebase(s"${appConf.getFirebaseUrl}/messages/$encodedEmail/admin/threads")
    FutureWrappers.getValueFuture(ref).map(snapshot => {
      val pairs = toChildrenList(snapshot).flatMap(thread => {
        toChildrenList(thread).map(message => {
          val threadId = thread.getKey
          val messageId = message.getKey
          (threadId, messageId)
        })
      })
      pairs.groupBy(_._1).mapValues(_.map(_._2))
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

