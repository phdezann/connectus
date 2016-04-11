package services

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

import conf.AppConf
import com.firebase.client.{DataSnapshot, Firebase}
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.common.base.Throwables
import common._
import model.{AttachmentRequest, Contact, GmailLabel, GmailMessage, InternetAddress, OutboxMessage, Resident, ThreadBundle}
import org.apache.commons.lang3.StringUtils
import play.api.Logger
import services.AccountInitializer.TradeSuccess
import services.Repository.{AuthorizationCodes, MessagesSnapshot, UserCredential}
import services.FirebaseConstants._

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FirebaseConstants {
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
  val OutboxPath = "outbox"
  val ResidentIdProperty = "id"
  val ResidentNameProperty = "name"
  val ResidentLabelNameProperty = "labelName"
  val ResidentLabelIdProperty = "labelId"

  val LoginCodeSuccess = "SUCCESS"
  val LoginCodeInvalidGrant = "INVALID_GRANT"
  val LoginCodeFailure = "FAILURE"
}

object Util {
  def foldToBlank[T](option: Option[T], f: T => String): String = option.fold[String]("")(f)
  def formatDateWithIsoFormatter(date: ZonedDateTime): String = {
    def removeZonedIdIfAny(date: String) = StringUtils.substringBefore(date, "[")
    removeZonedIdIfAny(date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
  }
  def encode(email: Email) = email.replace('.', ',')
  def decode(email: Email) = email.replace(',', '.')
}

object Repository {
  case class AuthorizationCodes(authorizationCodeId: String, androidId: String, authorizationCode: String, tradeCode: Option[String])
  case class UserCredential(refreshToken: String, accessToken: String, expirationTimeInMilliSeconds: Long)
  case class MessagesSnapshot(allThreadIds: Map[ThreadId, List[MessageId]] = Map(), messagesLabels: Map[MessageId, List[GmailLabel]] = Map())
}

@Singleton
class Repository @Inject()(firebaseFutureWrappers: FirebaseFutureWrappers, appConf: AppConf) {

  def connect = firebaseFutureWrappers.connect(appConf.getFirebaseUrl, appConf.getFirebaseJwtToken)

  def updateAccessToken(email: Email, accessToken: Option[String], expirationTimeInMilliSeconds: Option[Long]): Future[Unit] = {
    val encodedEmail = Util.encode(email)
    val values: Map[String, AnyRef] = Map(
      s"$UsersPath/$encodedEmail/$AccessTokenPath" -> accessToken.fold[String](null)(identity),
      s"$UsersPath/$encodedEmail/$ExpirationTimeMilliSecondsPath" -> expirationTimeInMilliSeconds.fold[java.lang.Long](null)(Long.box(_)))
    firebaseFutureWrappers.updateChildrenFuture(appConf.getFirebaseUrl, values)
  }

  def getCredentials(email: Email): Future[UserCredential] = {
    val encodedEmail = Util.encode(email)
    val url = s"${appConf.getFirebaseUrl}/$UsersPath/$encodedEmail"
    firebaseFutureWrappers.getValueFuture(url).map { dataSnapshot =>
      val refreshToken = dataSnapshot.child(RefreshTokenPath).getValue.asInstanceOf[String]
      val accessToken = dataSnapshot.child(AccessTokenPath).getValue.asInstanceOf[String]
      val expirationTimeInMilliSeconds = Long.unbox(dataSnapshot.child(ExpirationTimeMilliSecondsPath).getValue)
      UserCredential(refreshToken, accessToken, expirationTimeInMilliSeconds)
    }
  }

  def initAccount(tradeSuccess: TradeSuccess) = {
    val email = tradeSuccess.googleTokenResponse.parseIdToken().getPayload.getEmail
    def expirationTimeInMilliSeconds(expiresInSecondsFromNow: Long) = System.currentTimeMillis + expiresInSecondsFromNow * 1000
    val encodedEmail = Util.encode(email)
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/${tradeSuccess.authorizationCodes.authorizationCodeId}/$TradeLogPath/$CodePath" -> LoginCodeSuccess,
      s"$UsersPath/$encodedEmail/$RefreshTokenPath" -> tradeSuccess.googleTokenResponse.getRefreshToken,
      s"$UsersPath/$encodedEmail/$AccessTokenPath" -> tradeSuccess.googleTokenResponse.getAccessToken,
      s"$UsersPath/$encodedEmail/$ExpirationTimeMilliSecondsPath" -> Long.box(expirationTimeInMilliSeconds(tradeSuccess.googleTokenResponse.getExpiresInSeconds)))
    firebaseFutureWrappers.updateChildrenFuture(appConf.getFirebaseUrl, values)
  }

  def onTradeFailure(authorizationCodeId: String, e: Throwable) = {
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$CodePath" -> getCode(e),
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$MessagePath" -> Throwables.getStackTraceAsString(e))
    firebaseFutureWrappers.updateChildrenFuture(appConf.getFirebaseUrl, values)
  }

  private def getCode(exception: Throwable) = {
    exception match {
      case tre: TokenResponseException if tre.getDetails.getError == "invalid_grant" => LoginCodeInvalidGrant
      case _ => LoginCodeFailure
    }
  }

  def getMessagesSnapshot(email: Email): Future[MessagesSnapshot] = {
    def toChildrenList(snapshot: DataSnapshot) = snapshot.getChildren.iterator().asScala.toList
    val encodedEmail = Util.encode(email)
    val url = s"${appConf.getFirebaseUrl}/messages/$encodedEmail/admin/threads"
    firebaseFutureWrappers.getValueFuture(url).map(snapshot => {
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

  def saveMessages(values: Map[String, AnyRef]): Future[Unit] =
    firebaseFutureWrappers.updateChildrenFuture(appConf.getFirebaseUrl, values)

  def addResidentLabelId(email: Email, residentId: String, labelId: String): Future[Unit] = {
    val url = s"${appConf.getFirebaseUrl}/$ResidentsPath/${Util.encode(email)}/$residentId/$ResidentLabelIdProperty"
    firebaseFutureWrappers.setValueFuture(url, labelId)
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
    val residentsUrl = s"${appConf.getFirebaseUrl}/$ResidentsPath/${Util.encode(email)}"
    val contactsUrl = s"${appConf.getFirebaseUrl}/$ContactsPath/${Util.encode(email)}"
    val residents: Future[List[Resident]] = firebaseFutureWrappers.getValueFuture(residentsUrl).map(asResidents(_))
    val contacts: Future[List[Contact]] = firebaseFutureWrappers.getValueFuture(contactsUrl).map(asContacts(_))
    residents.zip(contacts).map(ee => merge(ee._1, ee._2))
  }

  def saveThreads(email: Email, threadBundles: List[ThreadBundle], messagesSnapshot: MessagesSnapshot, residentLabels: Map[Resident, GmailLabel]) = {
    val threadsDeletionValues = buildThreadsDeletionValues(email, threadBundles, messagesSnapshot.allThreadIds, residentLabels.keys.toList)
    val allDeletedMessageIds = findDeletedMessageIds(messagesSnapshot.allThreadIds, threadBundles)
    val values = threadBundles.flatMap { threadBundle =>
      val deletedMessageIds = allDeletedMessageIds.get(threadBundle.thread.id).fold[List[MessageId]](List())(identity)
      def adminThreadValues = buildThreadValues(email, adminContainerPath(email), threadBundle, residentLabels, deletedMessageIds, messagesSnapshot.messagesLabels)
      def buildResidentThreadValues =
        findResidentFromLabels(threadBundle.lastUntrashedMessage.get.labels, residentLabels).fold[Map[String, AnyRef]](Map())(resident => {
          buildThreadValues(email, residentContainerPath(email, resident), threadBundle, residentLabels, deletedMessageIds, messagesSnapshot.messagesLabels)
        })
      threadsDeletionValues ++ adminThreadValues ++ buildResidentThreadValues
    }.toMap
    val printableValues = TreeMap(values.toSeq: _*).mkString("\n")
    Logger.trace(s"Saving values: \n$printableValues")
    saveMessages(values)
  }

  def adminContainerPath(email: Email) = s"messages/${Util.encode(email)}/admin"

  def residentContainerPath(email: Email, resident: Resident) = s"messages/${Util.encode(email)}/${resident.id}"

  def findResidentFromLabels(labels: List[GmailLabel], residentLabels: Map[Resident, GmailLabel]): Option[Resident] =
    labels.flatMap { gmailLabel =>
      residentLabels.find { case (resident, label) => resident.labelId.fold(false)(labelId => gmailLabel.id == labelId) }
    }.headOption.map { case (resident, label) => resident }

  def buildThreadsDeletionValues(email: Email, threadBundles: List[ThreadBundle], adminThreadIds: Map[ThreadId, List[MessageId]], residents: List[Resident]) =
    findDeletedThreadIds(adminThreadIds, threadBundles).flatMap { threadId =>
      val forAdmin = Map[String, AnyRef](
        s"${adminContainerPath(email)}/inbox/${threadId}" -> null,
        s"${adminContainerPath(email)}/threads/${threadId}" -> null)
      val forResidents = residents.flatMap(resident =>
        Map(
          s"${residentContainerPath(email, resident)}/inbox/${threadId}" -> null,
          s"${residentContainerPath(email, resident)}/threads/${threadId}" -> null)).toMap[String, AnyRef]
      forAdmin ++ forResidents
    }.toMap

  def findDeletedThreadIds(adminThreadIds: Map[ThreadId, List[MessageId]], threadBundles: List[ThreadBundle]): List[ThreadId] =
    adminThreadIds.filter { case (threadId, messageIds) => !threadBundles.map(_.thread.id).contains(threadId) }.keys.toList

  def findDeletedMessageIds(adminThreadIds: Map[ThreadId, List[MessageId]], threadBundles: List[ThreadBundle]): Map[ThreadId, List[MessageId]] =
    adminThreadIds.flatten { case (threadId, messageIds) =>
      threadBundles.find(_.thread.id == threadId).find(_.thread.id == threadId).map(threadSummary => {
        val messages = threadSummary.messages
        val messageDeleted = messageIds.filter(!messages.map(_.id).contains(_))
        (threadId, messageDeleted)
      })
    }.toMap

  private def buildThreadValues(email: Email, containerPath: String, threadBundle: ThreadBundle, residentLabels: Map[Resident, GmailLabel], deletedMessageIds: List[MessageId], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val inboxValues = buildInboxValues(email, s"$containerPath/inbox", threadBundle, residentLabels, messagesLabels)
    val threadsValues = buildThreadsValues(s"$containerPath/threads", threadBundle, residentLabels, deletedMessageIds, messagesLabels)
    inboxValues ++ threadsValues
  }

  private def buildInboxValues(email: Email, inboxPath: String, threadBundle: ThreadBundle, residentLabels: Map[Resident, GmailLabel], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val threadSummaryPath = s"$inboxPath/${threadBundle.thread.id}"
    val threadSummaryInfoValues = Map[String, AnyRef](
      s"$threadSummaryPath/id" -> threadBundle.thread.id,
      s"$threadSummaryPath/snippet" -> threadBundle.thread.snippet)
    val lastMessagePath = s"$threadSummaryPath/lastMessage"
    val threadLastMessageValues = threadBundle.lastUntrashedMessage.fold[Map[String, AnyRef]](
      Map(lastMessagePath -> null))(lastMessage => buildMessageValues(lastMessagePath, lastMessage, residentLabels, messagesLabels))
    val contactEmailsPath = s"$threadSummaryPath/contactEmail"
    val contactEmailsValues = threadBundle.contactEmail(email).headOption.fold[Map[String, AnyRef]](
      Map(contactEmailsPath -> null))(contactEmail => Map(contactEmailsPath -> contactEmail))
    threadSummaryInfoValues ++ threadLastMessageValues ++ contactEmailsValues
  }

  private def buildThreadsValues(threadsPath: String, threadBundle: ThreadBundle, residentLabels: Map[Resident, GmailLabel], deletedMessageIds: List[MessageId], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val messagesValues = threadBundle.messages.flatMap { message => buildMessageValues(s"$threadsPath/${threadBundle.thread.id}/${message.id}", message, residentLabels, messagesLabels) }.toMap
    val messagesDeletionValues = deletedMessageIds.map { messageIds => s"$threadsPath/${threadBundle.thread.id}/${messageIds}" -> null }.toMap[String, AnyRef]
    messagesValues ++ messagesDeletionValues
  }

  private def buildMessageValues(messagePath: String, message: GmailMessage, residentLabels: Map[Resident, GmailLabel], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val labelsAsMap = message.labels.map { label => s"$messagePath/labels/${label.id}" -> label.name }.toMap
    val labelsDeletionsAsMap = messagesLabels
      .get(message.id)
      .map(currentLabels => currentLabels.filter(currentLabel => !message.labels.exists(_.id == currentLabel.id)))
      .map(_.map(label => s"$messagePath/labels/${label.id}" -> null).toMap)
      .fold(Map[String, AnyRef]())(identity)
    val attachmentsAsMap = message.attachments.map { attachment => {
      Map[String, AnyRef](
        s"$messagePath/attachments/partId${attachment.partId}/mimeType" -> attachment.mimeType,
        s"$messagePath/attachments/partId${attachment.partId}/filename" -> attachment.filename,
        s"$messagePath/attachments/partId${attachment.partId}/bodySize" -> Int.box(attachment.bodySize))
    }
    }.flatten.toMap
    val residentAsMap = residentLabels
      .find { case (resident, label) =>
        message.labels.find(gmailLabel => gmailLabel.name == label.name).isDefined
      }
      .map { case (resident, label) =>
        Map(
          s"$messagePath/resident/${ResidentIdProperty}" -> resident.id,
          s"$messagePath/resident/${ResidentNameProperty}" -> resident.name,
          s"$messagePath/resident/${ResidentLabelNameProperty}" -> resident.labelName)
      }.fold(Map[String, AnyRef](s"$messagePath/resident" -> null))(identity)
    val messagesAsMap = Map(
      s"$messagePath/from" -> Util.foldToBlank[InternetAddress](message.from, _.address),
      s"$messagePath/date" -> Util.foldToBlank[ZonedDateTime](message.date, Util.formatDateWithIsoFormatter(_)),
      s"$messagePath/subject" -> Util.foldToBlank[String](message.subject, identity),
      s"$messagePath/content" -> Util.foldToBlank[String](message.content, identity))
    labelsAsMap ++ labelsDeletionsAsMap ++ attachmentsAsMap ++ residentAsMap ++ messagesAsMap
  }

  def deleteOutboxMessage(email: Email, id: String) = {
    val url = s"${appConf.getFirebaseUrl}/${OutboxPath}/${Util.encode(email)}/${id}"
    firebaseFutureWrappers.setValueFuture(url, null)
  }

  def saveAttachmentResponse(email: Email, message: GmailMessage) = {
    def buildValues(accessToken: String) = {
      val clearRequest = Map(s"requests/${message.id}" -> null)
      val attachmentsAsMap = message.attachments.flatMap { attachment =>
        Map(
          s"responses/partId${attachment.partId}/url" -> s"https://www.googleapis.com/gmail/v1/users/$email/messages/${message.id}/attachments/${attachment.bodyAttachmentId}",
          s"responses/partId${attachment.partId}/accessToken" -> accessToken)
      }.toMap
      clearRequest ++ attachmentsAsMap
    }
    val url = s"${appConf.getFirebaseUrl}/attachments/${Util.encode(email)}"
    for {
      userCredential <- getCredentials(email)
      values = buildValues(userCredential.accessToken)
      _ <- firebaseFutureWrappers.updateChildrenFuture(url, values)
    } yield ()
  }
}

@Singleton
class RepositoryListeners @Inject()(firebaseFutureWrappers: FirebaseFutureWrappers, appConf: AppConf) {

  def listenForUsers(onUserAdded: Email => Unit, onUserRemoved: Email => Unit): FirebaseCancellable = {
    def toEmail(snapshot: DataSnapshot): String = Util.decode(snapshot.getKey)
    firebaseFutureWrappers.listenChildEvent(s"${appConf.getFirebaseUrl}/${UsersPath}", snapshot => onUserAdded(toEmail(snapshot)), snapshot => onUserRemoved(toEmail(snapshot)))
  }

  def listenForAuthorizationCodes(onAuthorizationCodesAdded: AuthorizationCodes => Unit): FirebaseCancellable =
    firebaseFutureWrappers.listenChildEvent(s"${appConf.getFirebaseUrl}/${AuthorizationCodesPath}", snapshot => {
      val providedAndroidId = snapshot.child(AndroidIdPath).getValue.asInstanceOf[String]
      val authorizationCode = snapshot.child(AuthorizationCodePath).getValue.asInstanceOf[String]
      val tradeCode = Option(snapshot.child(TradeLogPath).child(CodePath).getValue).asInstanceOf[Option[String]]
      onAuthorizationCodesAdded(AuthorizationCodes(snapshot.getKey, providedAndroidId, authorizationCode, tradeCode))
    })

  def listenForResidents(email: Email, onResidentAdded: Resident => Unit, onResidentRemoved: Resident => Unit): FirebaseCancellable = {
    def toResident(snapshot: DataSnapshot) = {
      val id = snapshot.getKey
      val name = snapshot.child(ResidentNameProperty).getValue.asInstanceOf[String]
      val labelName = snapshot.child(ResidentLabelNameProperty).getValue.asInstanceOf[String]
      val labelIdOpt = Option(snapshot.child(ResidentLabelIdProperty).getValue.asInstanceOf[String])
      Resident(id, name, labelName, labelIdOpt)
    }
    firebaseFutureWrappers.listenChildEvent(s"${appConf.getFirebaseUrl}/${ResidentsPath}/${Util.encode(email)}",
      snapshot => onResidentAdded(toResident(snapshot)),
      snapshot => onResidentRemoved(toResident(snapshot)))
  }

  def listenForContacts(email: Email, onContactsModified: List[Contact] => Unit): FirebaseCancellable = {
    val contactUrl = s"${appConf.getFirebaseUrl}/${ContactsPath}/${Util.encode(email)}"
    def callback: (DataSnapshot) => Unit = {
      snapshot => {
        firebaseFutureWrappers.getValueFuture(contactUrl).map { residentSnapshot =>
          val residentId = residentSnapshot.getKey
          val contacts: List[DataSnapshot] = residentSnapshot.getChildren.asScala.toList
          onContactsModified(contacts.map { contact => Contact(Util.decode(contact.getKey), residentId) })
        }
      }
    }
    firebaseFutureWrappers.listenChildEvent(contactUrl, callback, callback, callback)
  }

  def listenForOutboxMessages(email: Email, onMessageAdded: OutboxMessage => Unit): FirebaseCancellable = {
    val url = s"${appConf.getFirebaseUrl}/${OutboxPath}/${Util.encode(email)}"
    def callback: (DataSnapshot) => Unit = {
      snapshot => {
        val id = snapshot.getKey
        val residentId = snapshot.child("residentId").getValue.asInstanceOf[String]
        val threadId = snapshot.child("threadId").getValue.asInstanceOf[String]
        val to = snapshot.child("to").getValue.asInstanceOf[String]
        val personal = snapshot.child("personal").getValue.asInstanceOf[String]
        val subject = snapshot.child("subject").getValue.asInstanceOf[String]
        val content = snapshot.child("content").getValue.asInstanceOf[String]
        onMessageAdded(OutboxMessage(id, residentId, threadId, to, personal, subject, content))
      }
    }
    firebaseFutureWrappers.listenChildEvent(url, callback)
  }

  def listenForAttachmentRequests(email: Email, onAttachmentRequest: AttachmentRequest => Unit): FirebaseCancellable = {
    val url = s"${appConf.getFirebaseUrl}/attachments/${Util.encode(email)}/requests"
    def callback: (DataSnapshot) => Unit = {
      snapshot => {
        val messageId = snapshot.getKey
        onAttachmentRequest(AttachmentRequest(messageId))
      }
    }
    firebaseFutureWrappers.listenChildEvent(url, callback)
  }
}
