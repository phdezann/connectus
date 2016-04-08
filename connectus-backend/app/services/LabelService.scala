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
  val TrashedLabelName = "trashed"

  def allMessages =
    s"label:$InboxLabelName"

  def residentUntaggedMessages(contacts: List[Contact]) =
    s"label:$InboxLabelName ${buildContactQuery(contacts)}"

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

  def getOrCreateLabel(email: Email, resident: Resident, allLabels: List[GmailLabel]): Future[GmailLabel] = {
    val filter: (GmailLabel) => Boolean = (label) => resident.labelId.fold(false)(labelId => label.id == labelId)
    getOrCreateConnectusLabel(email, allLabels)
      .map(_ => listConnectusSubLabels(email, allLabels))
      .flatMap(connectusSubLabels => getOrCreate(email, connectusSubLabels, filter, LabelService.toLabelName(resident)))
  }

  def getOrCreateConnectusLabel(email: Email, allLabels: List[GmailLabel]): Future[GmailLabel] =
    getOrCreate(email, allLabels, _.name == LabelService.ConnectusLabelName, LabelService.ConnectusLabelName)

  private def getOrCreate(email: Email, labels: List[GmailLabel], filter: GmailLabel => Boolean, labelName: String): Future[GmailLabel] =
    labels.find(filter).fold[Future[GmailLabel]](mailClient.createLabel(email, labelName))(fs(_))

  def syncResidentLabels(email: Email, allLabels: List[GmailLabel]): Future[Map[Resident, GmailLabel]] =
    for {
      residents <- repository.getResidentsAndContacts(email)
      updatedResidents <- syncResidentLabels(email, residents.keySet.toList, allLabels)
      _ <- removeDanglingLabels(email, updatedResidents, allLabels)
    } yield updatedResidents

  def syncResidentLabels(email: Email, residents: List[Resident], allLabels: List[GmailLabel]): Future[Map[Resident, GmailLabel]] = {
    def createResidentLabel(resident: Resident): Future[(Resident, GmailLabel)] =
      for {
        newLabel <- mailClient.createLabel(email, LabelService.toLabelName(resident))
        _ <- updateResident(resident, newLabel)
      } yield copyLabelId(resident, newLabel)
    def updateResident(resident: Resident, label: GmailLabel): Future[Unit] =
      repository.addResidentLabelId(email, resident.id, label.id)
    def copyLabelId(resident: Resident, label: GmailLabel): (Resident, GmailLabel) =
      (resident.copy(labelId = Some(label.id)), label)

    val labels = listConnectusSubLabels(email, allLabels)
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

  def removeDanglingLabels(email: Email, residents: Map[Resident, GmailLabel], allLabels: List[GmailLabel]) = {
    val labels = listConnectusSubLabels(email, allLabels)
      .filter(label => !residents.keys.exists(_.labelId == Some(label.id)))
    deleteLabels(email, labels)
  }

  def listAllLabels(email: Email): Future[List[GmailLabel]] =
    mailClient.listLabels(email)

  private def listConnectusSubLabels(email: Email, allLabels: List[GmailLabel]): List[GmailLabel] =
    allLabels.filter(_.name.startsWith(LabelService.getSubLabelNamePrefix))

  def untagAll(email: Email, allLabels: List[GmailLabel]) = {
    val labels = listConnectusSubLabels(email, allLabels)
    mailClient.removeLabels(email, LabelService.allMessages, labels)
  }

  def deleteLabels(email: Email, labels: List[GmailLabel]) = {
    val all = labels.map(label => mailClient.deleteLabel(email, label))
    Future.sequence(all)
  }
}
