package services

import javax.inject.{Inject, Singleton}

import com.google.api.services.gmail.model.Label
import common._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AutoTagger @Inject()(gmailClient: GmailClient, messageService: MessageService, fireBaseFacade: FirebaseFacade, jobQueueActorClient: JobQueueActorClient) {

  fireBaseFacade.listenContacts(email => {
    jobQueueActorClient.schedule(() => {
      val removeTags = for {
        allLabels <- gmailClient.listLabels(email)
        allConnectusLabels <- filterConnectusLabels(allLabels)
        _ <- removeAll(email, allConnectusLabels)
      } yield ()
      val action = for {
        _ <- removeTags
        _ <- messageService.tagInbox(email)
      } yield ()
      action.onComplete { case _ => Logger.info(s"Messages successfully re-labelled for $email after contacts modification") }
      action.onFailure { case e => Logger.error(s"Error while re-labelling messages for $email after contacts modification", e) }
      action
    })
  })

  private def filterConnectusLabels(labels: List[Label]) =
    fs(labels.filter(_.getName.startsWith(MessageService.ConnectusLabelName)))

  private def removeAll(email: String, labels: List[Label]) = {
    val ops = labels.map(label => gmailClient.removeLabel(email, MessageService.allMessagesLabel, label))
    Future.sequence(ops)
  }
}
