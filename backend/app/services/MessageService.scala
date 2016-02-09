package services

import javax.inject.Inject

import com.google.api.services.gmail.model.Label
import common._
import model.{Contact, GmailMessage, Resident}
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

  def allMessagesLabel = s"label:$InboxLabelName"

  private def buildContactQuery(contacts: List[Contact]) =
    contacts
      .map(contact => "from:" + contact.email)
      .mkString("(", " OR ", ")")

  def toLabel(resident: Resident) = resident.name
}

class MessageService @Inject()(gmailClient: GmailClient, firebaseFacade: FirebaseFacade) {

  private def listNewMessages(email: String, query: String): Future[List[GmailMessage]] =
    gmailClient.listMessages(email, query)


  def executeTagInbox(email: Email) = {
    val action = tagInbox(email)
    action.onComplete { case _ => Logger.info(s"Messages successfully labelled for $email") }
    action.onFailure { case e => Logger.error(s"Error while labelling messages for $email", e) }
  }

  def tagInbox(email: Email) = {
    val residents: Future[Map[Resident, List[Contact]]] = firebaseFacade.getResidentsAndContacts(email)
    def initConnectusLabel: Future[Label] = getOrCreate(email, _.getName == MessageService.ConnectusLabelName, MessageService.ConnectusLabelName)
    def initLabel(resident: Resident): Future[Label] = getOrCreate(email, label => resident.labelId.fold(false)(labelId => label.getId == labelId), resident.name)
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
              messages <- listNewMessages(email, contactsQuery)
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
      _ <- updateMessages(email)
    } yield ()
  }

  def updateMessages(email: Email) = {
    for {
      _ <- saveMessagesForResidents(email)
      _ <- saveMessagesForAdmin(email)
    } yield ()
  }

  private def saveMessagesForResidents(email: Email) =
    firebaseFacade.getResidentsAndContacts(email).flatMap { residents =>
      val all: Iterable[Future[Unit]] = residents.map { case (resident, contacts) =>
        val query = MessageService.messagesLabel(contacts)
        listNewMessages(email, query)
          .map {toMap(email, resident.id, _)}
          .flatMap(firebaseFacade.saveMessages(_))
      }
      Future.sequence(all)
    }

  private def saveMessagesForAdmin(email: Email) = {
    val query = MessageService.allMessagesLabel
    listNewMessages(email, query)
      .map {toMap(email, "admin", _)}
      .flatMap(firebaseFacade.saveMessages(_))
  }

  private def toMap(email: Email, owner: String, messages: List[GmailMessage]): Map[String, AnyRef] =
    messages.flatMap { message =>
      Map(
        s"messages/${Util.encode(email)}/${owner}/${message.id}/from" -> message.from.get.address,
        s"messages/${Util.encode(email)}/${owner}/${message.id}/subject" -> message.subject.get,
        s"messages/${Util.encode(email)}/${owner}/${message.id}/content" -> message.content.get)
    }.toMap

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
