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
    if (contacts.isEmpty) {
      "label:no-contact"
    } else {
      contacts
        .map(contact => "from:" + contact.email)
        .mkString("(", " OR ", ")")
    }

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
    def initLabel(resident: Resident): Future[Label] = getOrCreate(email, label => resident.labelId.fold(false)(labelId => label.getId == labelId), MessageService.ConnectusLabelName + "/" + resident.name)
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
      _ <- updateMessages(email, residentLabels)
    } yield ()
  }

  def updateMessages(email: Email, residentLabels: Map[Resident, Label]) = {
    for {
      _ <- saveMessagesForResidents(email, residentLabels)
      _ <- saveMessagesForAdmin(email, residentLabels)
    } yield ()
  }

  private def saveMessagesForResidents(email: Email, residentLabels: Map[Resident, Label]) =
    firebaseFacade.getResidentsAndContacts(email).flatMap { residents =>
      val all: Iterable[Future[Unit]] = residents.map { case (resident, contacts) =>
        val query = MessageService.messagesLabel(contacts)
        listNewMessages(email, query)
          .map {toMap(email, resident.id, _, residentLabels)}
          .flatMap(firebaseFacade.saveMessages(_))
      }
      Future.sequence(all)
    }

  private def saveMessagesForAdmin(email: Email, residentLabels: Map[Resident, Label]) = {
    val query = MessageService.allMessagesLabel
    listNewMessages(email, query)
      .map {toMap(email, "admin", _, residentLabels)}
      .flatMap(firebaseFacade.saveMessages(_))
  }

  private def toMap(email: Email, owner: String, messages: List[GmailMessage], residentLabels: Map[Resident, Label]): Map[String, AnyRef] =
    messages.flatMap { message =>
      val labelsAsMap = message.labels.map { label => s"messages/${Util.encode(email)}/${owner}/${message.id}/labels/${label.id}" -> label.name }.toMap
      val residentAsMap = residentLabels
        .find { case (resident, label) => message.labels.find(gmailLabel => gmailLabel.name == label.getName).isDefined }
        .map { case (resident, label) =>
          Map(
            s"messages/${Util.encode(email)}/${owner}/${message.id}/resident/${firebaseFacade.ResidentIdProperty}" -> resident.id,
            s"messages/${Util.encode(email)}/${owner}/${message.id}/resident/${firebaseFacade.ResidentNameProperty}" -> resident.name,
            s"messages/${Util.encode(email)}/${owner}/${message.id}/resident/${firebaseFacade.ResidentLabelNameProperty}" -> resident.labelName)
        }.fold(Map[String, AnyRef](s"messages/${Util.encode(email)}/${owner}/${message.id}/resident" -> null))(identity)
      val messagesAsMap = Map(
        s"messages/${Util.encode(email)}/${owner}/${message.id}/from" -> message.from.get.address,
        s"messages/${Util.encode(email)}/${owner}/${message.id}/subject" -> message.subject.get,
        s"messages/${Util.encode(email)}/${owner}/${message.id}/content" -> message.content.get)
      labelsAsMap ++ residentAsMap ++ messagesAsMap
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
