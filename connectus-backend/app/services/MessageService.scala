package services

import javax.inject.Inject

import common._
import model.{GmailLabel, GmailMessage, OutboxMessage, Resident, ThreadBundle}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageService @Inject()(mailClient: MailClient, labelService: LabelService, repository: Repository) {

  def tagInbox(email: Email) = {
    def doLabelMessages(connectusLabel: GmailLabel, residentLabels: Map[Resident, GmailLabel]) = repository.getResidentsAndContacts(email).flatMap {
      residents =>
        val all: Iterable[Future[Unit]] = residents.map {
          case (resident, contacts) =>
            val contactsQuery = LabelService.residentUntaggedMessages(contacts)
            for {
              messages <- mailClient.listMessages(email, contactsQuery)
              _ <- mailClient.addLabels(email, contactsQuery, List(residentLabels(resident).id))
              _ <- mailClient.addLabels(email, contactsQuery, List(connectusLabel.id))
            } yield ()
        }
        Future.sequence(all)
    }
    for {
      connectusLabel <- labelService.getOrCreateConnectusLabel(email)
      residentsLabels <- labelService.syncResidentLabels(email)
      _ <- doLabelMessages(connectusLabel, residentsLabels)
      threadBundles <- getThreadBundles(email)
      messagesSnapshot <- repository.getMessagesSnapshot(email)
      _ <- repository.saveThreads(email, threadBundles, messagesSnapshot, residentsLabels)
    } yield ()
  }

  private def getThreadBundles(email: Email): Future[List[ThreadBundle]] = {
    val query = LabelService.allMessages
    mailClient.listThreads(email, query).flatMap { threads =>
      val messages = threads.map(thread =>
        mailClient
          .listMessagesOfThread(email, thread.id)
          .map(ThreadBundle(thread, _)))
      Future.sequence(messages)
    }
  }

  def reply(email: Email, outboxMessage: OutboxMessage): Future[Unit] = {
    def findResidentLabel(residentLabels: Map[Resident, GmailLabel]) = {
      labelService.syncResidentLabels(email).map {
        _.find { case (resident, label) => resident.id == outboxMessage.residentId }.map { case (_, label) => label }.get
      }
    }
    for {
      residentsLabels <- labelService.syncResidentLabels(email)
      connectusLabel <- labelService.getOrCreateConnectusLabel(email)
      residentsLabel <- findResidentLabel(residentsLabels)
      labels = List(residentsLabel, connectusLabel)
      _ <- mailClient.reply(email, labels, outboxMessage.threadId, outboxMessage.to, outboxMessage.personal, outboxMessage.content)
      threadBundles <- getThreadBundles(email)
      messagesSnapshot <- repository.getMessagesSnapshot(email)
      _ <- repository.saveThreads(email, threadBundles, messagesSnapshot, residentsLabels) //** TODO **/
    } yield ()
  }
}
