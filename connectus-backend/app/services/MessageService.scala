package services

import javax.inject.Inject

import com.google.api.services.gmail.model.Label
import common._
import model.{Contact, GmailLabel, GmailMessage, GmailThread, Resident}
import play.api.Logger

import scala.collection.immutable.Iterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MessageService {
  val InboxLabelName = "inbox"
  val ConnectusLabelName = "connectus"

  def newMessagesLabel(contacts: List[Contact]) =
    s"label:$InboxLabelName -label:$ConnectusLabelName ${buildContactQuery(contacts)}"

  def messagesLabel(contacts: List[Contact]) =
    s"label:$InboxLabelName label:$ConnectusLabelName ${buildContactQuery(contacts)}"

  def allConnectusMessages =
    s"label:$InboxLabelName label:$ConnectusLabelName"

  def allMessagesLabel = s"label:$InboxLabelName"

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

class MessageService @Inject()(gmailClient: GmailClient, firebaseFacade: FirebaseFacade) {

  def executeTagInbox(email: Email) = {
    val action = tagInbox(email)
    action.onComplete { case _ => Logger.info(s"Messages successfully labelled for $email") }
    action.onFailure { case e => Logger.error(s"Error while labelling messages for $email", e) }
  }

  def tagInbox(email: Email) = {
    val residents: Future[Map[Resident, List[Contact]]] = firebaseFacade.getResidentsAndContacts(email)
    def initConnectusLabel: Future[Label] = getOrCreate(email, _.getName == MessageService.ConnectusLabelName, MessageService.ConnectusLabelName)
    def initLabel(resident: Resident): Future[Label] = getOrCreate(email, label => resident.labelId.fold(false)(labelId => label.getId == labelId), MessageService.toLabel(resident))
    def addLabel(query: String, label: Label): Future[_] = gmailClient.addLabel(email, query, label)
    def saveResidentLabelId(resident: Resident, label: Label): Future[Unit] = firebaseFacade.addResidentLabelId(email, resident.id, label.getId)
    def initAllResidentLabels: Future[Map[Resident, Label]] = residents.flatMap { residents =>
      val all = residents.map { case (resident, contacts) => initLabel(resident).map(label => (resident, label)) }
      Future.sequence(all).map(_.toMap)
    }
    def initAllLabels = for {
      connectusLabel <- initConnectusLabel
      residents <- initAllResidentLabels
    } yield (connectusLabel, residents)
    def doLabelMessages(connectusLabel: Label, residentLabels: Map[Resident, Label]) = residents.flatMap {
      residents =>
        val all: Iterable[Future[Unit]] = residents.map {
          case (resident, contacts) =>
            val contactsQuery = MessageService.newMessagesLabel(contacts)
            for {
              messages <- gmailClient.listMessages(email, contactsQuery)
              _ <- addLabel(contactsQuery, residentLabels(resident))
              _ <- addLabel(contactsQuery, connectusLabel)
              _ <- saveResidentLabelId(resident, residentLabels(resident))
            } yield ()
        }
        Future.sequence(all)
    }
    for {
      (connectusLabel, residentLabels) <- initAllLabels
      _ <- doLabelMessages(connectusLabel, residentLabels)
      _ <- saveThreads(email, residentLabels)
    } yield ()
  }

  case class ThreadSummary(thread: GmailThread, messages: List[GmailMessage], lastMessage: Option[GmailMessage] = None)

  private def saveThreads(email: Email, residentLabels: Map[Resident, Label]) = {
    def augmentThreadSummariesWithLastMessage(threadSummaries: List[ThreadSummary]): Future[List[ThreadSummary]] = {
      val withLastMessage = threadSummaries.map { threadSummary =>
        threadSummary.messages.reverse.headOption.fold[Future[ThreadSummary]](fs(threadSummary)) { lastMessage =>
          gmailClient.getMessage(email, lastMessage.id).map { gmailMessage =>
            threadSummary.copy(lastMessage = gmailMessage)
          }
        }
      }
      Future.sequence(withLastMessage)
    }

    val query = MessageService.allConnectusMessages
    val threadSummaries = gmailClient.listThreads(email, query).flatMap { threads =>
      val messages = threads.map(thread => gmailClient.listMessagesOfThread(email, thread.id).map(ThreadSummary(thread, _)))
      Future.sequence(messages)
    }
    threadSummaries
      .flatMap { threadSummaries => augmentThreadSummariesWithLastMessage(threadSummaries) }
      .map { threadSummaries =>
        def findResidentByLabel(labels: List[GmailLabel]): Option[Resident] =
          labels.flatMap { gmailLabel =>
            residentLabels.find { case (resident, label) => resident.labelId.fold(false)(labelId => gmailLabel.id == labelId) }
          }.headOption.map { case (resident, label) => resident }
        threadSummaries.flatMap { threadSummary =>
          def valuesForAdmin = toMap(email, "admin", threadSummary, residentLabels)
          findResidentByLabel(threadSummary.lastMessage.get.labels).fold[Map[String, AnyRef]](valuesForAdmin)(resident =>
            valuesForAdmin ++ toMap(email, resident.id, threadSummary, residentLabels) ++ valuesForAdmin.toList)
        }.toMap
      }.flatMap(firebaseFacade.saveMessages(_))
  }

  private def toMap(email: Email, owner: String, threadSummary: ThreadSummary, residentLabels: Map[Resident, Label]): Map[String, AnyRef] = {
    val path = s"messages/${Util.encode(email)}/${owner}"
    val lastMessagePath = s"$path/inbox/${threadSummary.thread.id}/lastMessage"
    val threadLastMessage = threadSummary.lastMessage.fold[Map[String, AnyRef]](
      Map(lastMessagePath -> null))(lastMessage => toMap(lastMessagePath, threadSummary.thread, lastMessage, residentLabels))
    val threadInfoAsMap = Map[String, AnyRef](
      s"$path/inbox/${threadSummary.thread.id}/id" -> threadSummary.thread.id,
      s"$path/inbox/${threadSummary.thread.id}/snippet" -> threadSummary.thread.snippet)
    val messagesAsMap = toMap(path, threadSummary.thread, threadSummary.messages, residentLabels)
    threadLastMessage ++ threadInfoAsMap ++ messagesAsMap
  }

  private def toMap(path: String, thread: GmailThread, messages: List[GmailMessage], residentLabels: Map[Resident, Label]): Map[String, AnyRef] =
    messages.flatMap { message => toMap(s"$path/threads/${thread.id}/${message.id}", thread, message, residentLabels) }.toMap

  private def toMap(path: String, thread: GmailThread, message: GmailMessage, residentLabels: Map[Resident, Label]): Map[String, AnyRef] = {
    val labelsAsMap = message.labels.map { label => s"$path/labels/${label.id}" -> label.name }.toMap
    val residentAsMap = residentLabels
      .find { case (resident, label) => message.labels.find(gmailLabel => gmailLabel.name == label.getName).isDefined }
      .map { case (resident, label) =>
        Map(
          s"$path/resident/${firebaseFacade.ResidentIdProperty}" -> resident.id,
          s"$path/resident/${firebaseFacade.ResidentNameProperty}" -> resident.name,
          s"$path/resident/${firebaseFacade.ResidentLabelNameProperty}" -> resident.labelName)
      }.fold(Map[String, AnyRef](s"$path/threads/${thread.id}/${message.id}/resident" -> null))(identity)
    val messagesAsMap = Map(
      s"$path/from" -> message.from.get.address,
      s"$path/date" -> message.date.get.toString,
      s"$path/subject" -> message.subject.get,
      s"$path/content" -> message.content.get)

    labelsAsMap ++ residentAsMap ++ messagesAsMap
  }

  private def getOrCreate(email: Email, filter: Label => Boolean, labelName: String): Future[Label] = {
    gmailClient.listLabels(email).flatMap { labels =>
      val labelOpt = labels.find(filter)
      if (labelOpt.isDefined) {
        fs(labelOpt.get)
      } else {
        gmailClient.createLabel(email, labelName)
      }
    }
  }
}
