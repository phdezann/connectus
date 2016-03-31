package services

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.google.api.services.gmail.model.Label
import common._
import model.{Contact, GmailLabel, GmailMessage, InternetAddress, Resident, ThreadBundle}
import play.api.Logger
import FirebaseFacade._
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MessageService {
  val InboxLabelName = "inbox"
  val ConnectusLabelName = "connectus"

  def residentUntaggedMessages(contacts: List[Contact]) =
    s"label:$InboxLabelName -label:$ConnectusLabelName ${buildContactQuery(contacts)}"

  def allMessages = s"label:$InboxLabelName"

  private def buildContactQuery(contacts: List[Contact]) =
    if (contacts.isEmpty) {
      "label:no-contact"
    } else {
      contacts
        .map(contact => "from:" + contact.email)
        .mkString("(", " OR ", ")")
    }

  def toLabel(resident: Resident) = MessageService.ConnectusLabelName + "/" + resident.name
}

class MessageService @Inject()(gmailClient: GmailClient, firebaseFacade: FirebaseFacade, messageMapper: MessageMapper) {

  def tagInbox(email: Email) = {
    def initConnectusLabel: Future[GmailLabel] = getOrCreate(email, _.getName == MessageService.ConnectusLabelName, MessageService.ConnectusLabelName)
    def initLabel(resident: Resident): Future[GmailLabel] = getOrCreate(email, label => resident.labelId.fold(false)(labelId => label.getId == labelId), MessageService.toLabel(resident))
    def addLabel(query: String, label: GmailLabel): Future[_] = gmailClient.addLabels(email, query, List(label.id))
    def saveResidentLabelId(resident: Resident, label: GmailLabel): Future[Unit] = firebaseFacade.addResidentLabelId(email, resident.id, label.id)
    def initAllResidentLabels(residents: Map[Resident, List[Contact]]): Future[Map[Resident, GmailLabel]] = {
      val all = residents.map { case (resident, contacts) =>
        initLabel(resident)
          .flatMap(label => saveResidentLabelId(resident, label).map(_ => label))
          .map(label => (resident.copy(labelId = Some(label.id)), label))
      }
      Future.sequence(all).map(_.toMap)
    }
    def initAllLabels(residents: Map[Resident, List[Contact]]): Future[(GmailLabel, Map[Resident, GmailLabel])] = for {
      connectusLabel <- initConnectusLabel
      residents <- initAllResidentLabels(residents)
    } yield (connectusLabel, residents)
    def doLabelMessages(connectusLabel: GmailLabel, residentLabels: Map[Resident, GmailLabel]) = firebaseFacade.getResidentsAndContacts(email).flatMap {
      residents =>
        val all: Iterable[Future[Unit]] = residents.map {
          case (resident, contacts) =>
            val contactsQuery = MessageService.residentUntaggedMessages(contacts)
            for {
              messages <- gmailClient.listMessages(email, contactsQuery)
              _ <- addLabel(contactsQuery, residentLabels(resident))
              _ <- addLabel(contactsQuery, connectusLabel)
            } yield ()
        }
        Future.sequence(all)
    }
    for {
      residents <- firebaseFacade.getResidentsAndContacts(email)
      (connectusLabel, residentLabels) <- initAllLabels(residents)
      _ <- doLabelMessages(connectusLabel, residentLabels)
      _ <- saveThreads(email, residentLabels)
    } yield ()
  }

  def saveThreads(email: Email, residentLabels: Map[Resident, GmailLabel]) = {
    val threadBundles = messageMapper.getThreadBundles(email)
    val adminThreadIds = firebaseFacade.getMessagesSnapshot(email)
    threadBundles.zip(adminThreadIds).map { case (threadBundles, snapshot) =>
      val threadsDeletionValues = buildThreadsDeletionValues(email, threadBundles, snapshot.allThreadIds, residentLabels.keys.toList)
      val allDeletedMessageIds = findDeletedMessageIds(snapshot.allThreadIds, threadBundles)
      threadBundles.flatMap { threadBundle =>
        val deletedMessageIds = allDeletedMessageIds.get(threadBundle.thread.id).fold[List[MessageId]](List())(identity)
        def adminThreadValues = buildThreadValues(adminContainerPath(email), threadBundle, residentLabels, deletedMessageIds, snapshot.messagesLabels)
        def buildResidentThreadValues =
          findResidentFromLabels(threadBundle.lastUntrashedMessage.get.labels, residentLabels).fold[Map[String, AnyRef]](Map())(resident => {
            buildThreadValues(residentContainerPath(email, resident), threadBundle, residentLabels, deletedMessageIds, snapshot.messagesLabels)
          })
        threadsDeletionValues ++ adminThreadValues ++ buildResidentThreadValues
      }.toMap
    }.flatMap { values =>
      val printableValues = TreeMap(values.toSeq: _*).mkString("\n")
      Logger.debug(s"Saving values: \n$printableValues")
      firebaseFacade.saveMessages(values)
    }
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

  private def buildThreadValues(containerPath: String, threadBundle: ThreadBundle, residentLabels: Map[Resident, GmailLabel], deletedMessageIds: List[MessageId], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val inboxValues = buildInboxValues(s"$containerPath/inbox", threadBundle, residentLabels, messagesLabels)
    val threadsValues = buildThreadsValues(s"$containerPath/threads", threadBundle, residentLabels, deletedMessageIds, messagesLabels)
    inboxValues ++ threadsValues
  }

  private def buildInboxValues(inboxPath: String, threadBundle: ThreadBundle, residentLabels: Map[Resident, GmailLabel], messagesLabels: Map[MessageId, List[GmailLabel]]): Map[String, AnyRef] = {
    val threadSummaryPath = s"$inboxPath/${threadBundle.thread.id}"
    val threadSummaryInfoValues = Map[String, AnyRef](
      s"$threadSummaryPath/id" -> threadBundle.thread.id,
      s"$threadSummaryPath/snippet" -> threadBundle.thread.snippet)
    val lastMessagePath = s"$threadSummaryPath/lastMessage"
    val threadLastMessageValues = threadBundle.lastUntrashedMessage.fold[Map[String, AnyRef]](
      Map(lastMessagePath -> null))(lastMessage => buildMessageValues(lastMessagePath, lastMessage, residentLabels, messagesLabels))
    threadSummaryInfoValues ++ threadLastMessageValues
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
      s"$messagePath/from" -> foldToBlank[InternetAddress](message.from, _.address),
      s"$messagePath/date" -> foldToBlank[ZonedDateTime](message.date, formatDateWithIsoFormatter(_)),
      s"$messagePath/subject" -> foldToBlank[String](message.subject, identity),
      s"$messagePath/content" -> foldToBlank[String](message.content, identity))
    labelsAsMap ++ labelsDeletionsAsMap ++ residentAsMap ++ messagesAsMap
  }

  private def foldToBlank[T](option: Option[T], f: T => String): String = option.fold[String]("")(f)

  private def formatDateWithIsoFormatter(date: ZonedDateTime): String = {
    def removeZonedIdIfAny(date: String) = StringUtils.substringBefore(date, "[")
    removeZonedIdIfAny(date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
  }

  private def getOrCreate(email: Email, filter: Label => Boolean, labelName: String): Future[GmailLabel] = {
    gmailClient.listLabels(email).flatMap { labels =>
      val labelOpt = labels.find(filter)
      if (labelOpt.isDefined) {
        val label: Label = labelOpt.get
        fs(GmailLabel(label.getId, label.getName))
      } else {
        gmailClient.createLabel(email, labelName)
          .map(label => GmailLabel(label.getId, label.getName))
      }
    }
  }
}

class MessageMapper @Inject()(gmailClient: GmailClient) {
  def getThreadBundles(email: Email): Future[List[ThreadBundle]] = {
    val query = MessageService.allMessages
    gmailClient.listThreads(email, query).flatMap { threads =>
      val messages = threads.map(thread =>
        gmailClient
          .listMessagesOfThread(email, thread.id)
          .map(ThreadBundle(thread, _)))
      Future.sequence(messages)
    }
  }
}
