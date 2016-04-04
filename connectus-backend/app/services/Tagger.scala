package services

import javax.inject.{Inject, Singleton}

import com.google.api.services.gmail.model.Label
import common._
import model.{GmailLabel, Resident}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Tagger @Inject()(gmailClient: GmailClient, messageService: MessageService, firebaseFacade: FirebaseFacade, jobQueueActorClient: JobQueueActorClient) {

  def createConnectusTag(email: Email): Future[GmailLabel] = initConnectusLabel(email)

  def createTag(email: Email, resident: Resident): Future[GmailLabel] = initConnectusLabel(email).flatMap( _ => initLabel(email, resident).flatMap(label => firebaseFacade.addResidentLabelId(email, resident.id, label.id).map(_=>label)))

  def initConnectusLabel(email: Email): Future[GmailLabel] = getOrCreate(email, _.getName == MessageService.ConnectusLabelName, MessageService.ConnectusLabelName)

  def initLabel(email: Email, resident: Resident): Future[GmailLabel] = getOrCreate(email, label => resident.labelId.fold(false)(labelId => label.getId == labelId), MessageService.toLabel(resident))

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

  def deleteTag(email: Email, resident: Resident): Future[GmailLabel] = ???

  private def filterConnectusLabels(labels: List[Label]) =
    fs(labels.filter(_.getName.startsWith(MessageService.ConnectusLabelName)))

  private def removeAll(email: String, labels: List[Label]) =
    gmailClient.removeLabels(email, MessageService.allMessages, labels)
}
