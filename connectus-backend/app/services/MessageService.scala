package services

import javax.inject.{Inject, Singleton}

import common._
import model.{AttachmentRequest, GmailLabel, OutboxMessage, Resident, ThreadBundle}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MessageService @Inject()(mailClient: MailClient, labelService: LabelService, repository: Repository, historyIdService: HistoryIdService) {

  def tagInbox(email: Email, receivedHistoryId: BigInt): Future[Option[BigInt]] = {
    Logger.info(s"Initiating tagInbox with receivedHistoryId=$receivedHistoryId for $email")

    historyIdService.getLocalHistoryId(email).flatMap {
      localHistoryId => (localHistoryId, receivedHistoryId) match {
        case (None, _) =>
          Logger.info(s"Local historyId unknown, tagging the inbox for $email")
          tagInbox(email, Some(receivedHistoryId))
        case (Some(local), received) if local < received =>
          Logger.info(s"Local historyId $local saved from previous calls is older than the received one $received, tagging the inbox for $email")
          tagInbox(email, Some(receivedHistoryId))
        case (Some(local), received) =>
          Logger.info(s"Local historyId $local and received historyId $received indicate that tagging the inbox can be skipped for $email")
          fs((Some(local)))
      }
    }
  }

  def tagInbox(email: Email, receivedHistoryId: Option[BigInt] = None): Future[Option[BigInt]] = {
    Logger.info(s"Initiating tagInbox for $email")

    def doLabelMessages(connectusLabel: GmailLabel, residentLabels: Map[Resident, GmailLabel]) =
      repository.getResidentsAndContacts(email)
        .flatMap { residents =>
          val all: Iterable[Future[Unit]] = residents.map {
            case (resident, contacts) =>
              val contactsQuery = LabelService.residentUntaggedMessages(contacts)
              for {
                _ <- mailClient.addLabels(email, contactsQuery, List(residentLabels(resident)))
                _ <- mailClient.addLabels(email, contactsQuery, List(connectusLabel))
              } yield ()
          }
          Future.sequence(all)
        }

    def removeTrashedMessages(threadBundles: List[ThreadBundle]): List[ThreadBundle] =
      threadBundles.map(threadBundle => threadBundle.copy(messages = threadBundle.messages.filter(!_.labels.contains(LabelService.TrashLabelName))))

    for {
      allLabels <- labelService.listAllLabels(email)
      _ <- labelService.untagAll(email, allLabels)
      connectusLabel <- labelService.getOrCreateConnectusLabel(email, allLabels)
      residentsLabels <- labelService.syncResidentLabels(email, allLabels)
      _ <- doLabelMessages(connectusLabel, residentsLabels)
      threadBundles <- getThreadBundles(email, allLabels)
      filteredThreadBundles = removeTrashedMessages(threadBundles)
      messagesSnapshot <- repository.getMessagesSnapshot(email)
      _ <- repository.saveThreads(email, filteredThreadBundles, messagesSnapshot, residentsLabels)
      newHistoryId <- historyIdService.updateLocalHistory(email, receivedHistoryId)
    } yield newHistoryId
  }

  private def getThreadBundles(email: Email, allLabels: List[GmailLabel]): Future[List[ThreadBundle]] = {
    val query = LabelService.allMessages
    mailClient.listThreads(email, query).flatMap { threads =>
      val messages = threads.map(thread =>
        mailClient
          .listMessagesOfThread(email, thread.id, allLabels)
          .map(ThreadBundle(thread, _)))
      Future.sequence(messages)
    }
  }

  def reply(email: Email, outboxMessage: OutboxMessage): Future[Unit] =
    for {
      allLabels <- labelService.listAllLabels(email)
      message <- mailClient.reply(email, outboxMessage.threadId, outboxMessage.to, outboxMessage.personal, outboxMessage.subject, outboxMessage.content, allLabels)
      _ <- repository.deleteOutboxMessage(email, outboxMessage.id)
      _ <- tagInbox(email, message.getHistoryId)
    } yield ()

  def prepareRequest(email: Email, attachmentRequest: AttachmentRequest): Future[Unit] =
    mailClient.getMessage(email, attachmentRequest.messageId, List())
      .flatMap(freshMessage => repository.saveAttachmentResponse(email, freshMessage))
}

@Singleton
class HistoryIdService @Inject()(mailClient: MailClient, actorsClient: ActorsClient) {

  def getLocalHistoryId(email: Email): Future[Option[BigInt]] = actorsClient.getHistoryId(email)

  def updateLocalHistory(email: Email, previousHistoryId: Option[BigInt]) =
    for {
      newHistoryId <- getLastRemoteHistoryId(email, previousHistoryId)
      _ <- actorsClient.setHistoryId(email, newHistoryId)
    } yield newHistoryId

  private def getLastRemoteHistoryId(email: Email, previousHistoryId: Option[BigInt]): Future[Option[BigInt]] = {
    if (previousHistoryId.isDefined) {
      mailClient.getLastHistoryId(email, previousHistoryId.get).map(Some(_))
    } else {
      mailClient.listThreads(email, LabelService.allMessages)
        .map(_.headOption)
        .flatMap { latestThreadOpt =>
          if (latestThreadOpt.isDefined) {
            mailClient.getLastHistoryId(email, latestThreadOpt.get.historyId).map(Some(_))
          } else {
            fs(None)
          }
        }
    }
  }
}
