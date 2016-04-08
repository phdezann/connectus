package services

import javax.inject.{Inject, Singleton}

import common._
import model.{Contact, GmailLabel, Resident}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LabelService {
  val InboxLabelName = "inbox"
  val ConnectusLabelName = "connectus"
  val AbsentLabelName = "query-returns-nothing"

  def allMessages =
    s"label:$InboxLabelName"

  def residentUntaggedMessages(contacts: List[Contact]) =
    s"label:$InboxLabelName -label:$ConnectusLabelName ${buildContactQuery(contacts)}"

  private def buildContactQuery(contacts: List[Contact]) =
    if (contacts.isEmpty) {
      s"label:$AbsentLabelName"
    } else {
      contacts
        .map(contact => "from:" + contact.email)
        .mkString("(", " OR ", ")")
    }

  def getSubLabelNamePrefix = LabelService.ConnectusLabelName + "/"

  def toLabelName(resident: Resident) = getSubLabelNamePrefix + resident.name
}

@Singleton
class LabelService @Inject()(mailClient: MailClient, repository: Repository) {

  def getOrCreateConnectusLabel(email: Email) = {
    listAllLabels(email).flatMap(allLabels => getOrCreate(email, allLabels, _.name == LabelService.ConnectusLabelName, LabelService.ConnectusLabelName))
  }

  def getOrCreateLabel(email: Email, resident: Resident) = {
    val filter: (GmailLabel) => Boolean = (label) => resident.labelId.fold(false)(labelId => label.id == labelId)
    getOrCreateConnectusLabel(email)
      .flatMap(_ => listConnectusSubLabels(email)
        .flatMap(connectusSubLabels => getOrCreate(email, connectusSubLabels, filter, LabelService.toLabelName(resident))))
  }

  private def getOrCreate(email: Email, labels: List[GmailLabel], filter: GmailLabel => Boolean, labelName: String): Future[GmailLabel] =
    labels.find(filter).fold[Future[GmailLabel]](mailClient.createLabel(email, labelName))(fs(_))

  def syncResidentLabels(email: Email): Future[Map[Resident, GmailLabel]] =
    for {
      residents <- repository.getResidentsAndContacts(email)
      updatedResidents <- syncResidentLabels(email, residents.keySet.toList)
      _ <- removeDanglingLabels(email, updatedResidents)
    } yield updatedResidents

  def syncResidentLabels(email: Email, residents: List[Resident]): Future[Map[Resident, GmailLabel]] = {
    def createResidentLabel(resident: Resident): Future[(Resident, GmailLabel)] =
      for {
        newLabel <- mailClient.createLabel(email, LabelService.toLabelName(resident))
        _ <- updateResident(resident, newLabel)
      } yield copyLabelId(resident, newLabel)
    def updateResident(resident: Resident, label: GmailLabel): Future[Unit] =
      repository.addResidentLabelId(email, resident.id, label.id)
    def copyLabelId(resident: Resident, label: GmailLabel): (Resident, GmailLabel) =
      (resident.copy(labelId = Some(label.id)), label)

    listConnectusSubLabels(email).flatMap { labels => {
      val pairs = residents.map { resident =>
        if (resident.labelId.isDefined) {
          val residentLabelId: String = resident.labelId.get
          val labelOpt = labels.find(_.id == residentLabelId)
          if (labelOpt.isDefined) {
            fs((resident, labelOpt.get))
          } else {
            createResidentLabel(resident)
          }
        } else {
          val labelOpt = labels.find(_.name == LabelService.toLabelName(resident))
          if (labelOpt.isDefined) {
            val label = labelOpt.get
            updateResident(resident, label).map(_ => copyLabelId(resident, label))
          } else {
            createResidentLabel(resident)
          }
        }
      }
      Future.sequence(pairs).map(_.toMap)
    }
    }
  }

  def removeDanglingLabels(email: Email, residents: Map[Resident, GmailLabel]) = {
    def deleteLabels(labels: List[GmailLabel]) = {
      val all = labels.map(label => mailClient.deleteLabel(email, label))
      Future.sequence(all)
    }
    for {
      labels <- listConnectusSubLabels(email)
      danglingLabels = labels.filter(label => !residents.keys.exists(_.labelId == Some(label.id)))
      _ <- deleteLabels(danglingLabels)
    } yield residents
  }

  private def listAllLabels(email: Email) =
    mailClient.listLabels(email)

  private def listConnectusSubLabels(email: Email) =
    listAllLabels(email).map(_.filter(_.name.startsWith(LabelService.getSubLabelNamePrefix)))
}
