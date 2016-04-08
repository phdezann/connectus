package services

import javax.inject.{Inject, Singleton}

import common._
import model.{GmailLabel, OutboxMessage, Resident, ThreadBundle}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MessageService @Inject()(mailClient: MailClient, labelService: LabelService, repository: Repository) {

  var historyIdOpt: Option[BigInt] = None

  def tagInbox(email: Email, ignoreHistoryId: Boolean = false): Future[Unit] = {
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
      threadBundles.map(threadBundle => threadBundle.copy(messages = threadBundle.messages.filter(!_.labels.contains(LabelService.TrashedLabelName))))

    def doTagInbox(newHistoryId: BigInt): Future[Unit] = for {
      allLabels <- labelService.listAllLabels(email)
      _ <- labelService.untagAll(email, allLabels)
      connectusLabel <- labelService.getOrCreateConnectusLabel(email, allLabels)
      residentsLabels <- labelService.syncResidentLabels(email, allLabels)
      _ <- doLabelMessages(connectusLabel, residentsLabels)
      threadBundles <- getThreadBundles(email, allLabels)
      filteredThreadBundles = removeTrashedMessages(threadBundles)
      messagesSnapshot <- repository.getMessagesSnapshot(email)
      _ <- repository.saveThreads(email, filteredThreadBundles, messagesSnapshot, residentsLabels)
    } yield {
      historyIdOpt = Some(newHistoryId)
    }

    def getLatestHistoryId(email: Email): Future[Option[BigInt]] = {
      if (historyIdOpt.isDefined) {
        mailClient.getLastHistoryId(email, historyIdOpt.get).map(Some(_))
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

    getLatestHistoryId(email).flatMap { latestHistoryIdOpt =>
      (ignoreHistoryId, historyIdOpt, latestHistoryIdOpt) match {
        case (true, _, Some(latestHistoryId)) =>
          Logger.info(s"Ignoring historyId, tagging the inbox with latestHistoryId ${latestHistoryId}")
          doTagInbox(latestHistoryId)
        case (false, None, Some(latestHistoryId)) =>
          Logger.info(s"No historyId saved from previous calls, tagging the inbox with latestHistoryId ${latestHistoryId}")
          doTagInbox(latestHistoryId)
        case (false, Some(historyId), Some(latestHistoryId)) if latestHistoryId > historyId =>
          Logger.info(s"historyId ${historyId} saved from previous calls is older than the current one ${latestHistoryId}, tagging the inbox")
          doTagInbox(latestHistoryId)
        case (false, Some(historyId), Some(latestHistoryId)) if historyId <= latestHistoryId =>
          Logger.info(s"historyId ${historyId} saved from previous calls and current one ${latestHistoryId} indicates that tagging the inbox can be skipped")
          fs(())
      }
    }
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
      _ <- mailClient.reply(email, outboxMessage.threadId, outboxMessage.to, outboxMessage.personal, outboxMessage.subject, outboxMessage.content, allLabels)
      _ <- repository.deleteOutboxMessage(email, outboxMessage.id)
      _ <- tagInbox(email)
    } yield ()
}
