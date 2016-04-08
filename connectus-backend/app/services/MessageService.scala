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

  def tagInbox(email: Email): Future[Unit] = {
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

    def doTagInbox(newHistoryId: BigInt): Future[Unit] = for {
      connectusLabel <- labelService.getOrCreateConnectusLabel(email)
      residentsLabels <- labelService.syncResidentLabels(email)
      _ <- doLabelMessages(connectusLabel, residentsLabels)
      threadBundles <- getThreadBundles(email)
      messagesSnapshot <- repository.getMessagesSnapshot(email)
      _ <- repository.saveThreads(email, threadBundles, messagesSnapshot, residentsLabels)
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

    getLatestHistoryId(email).flatMap { latestHistoryId =>
      (historyIdOpt, latestHistoryId) match {
        case (None, Some(latestHistoryId)) =>
          Logger.info(s"No historyId saved from previous calls, tagging the inbox with latestHistoryId ${latestHistoryId}")
          doTagInbox(latestHistoryId)
        case (Some(historyId), Some(latestHistoryId)) if latestHistoryId > historyId =>
          Logger.info(s"historyId ${historyId} saved from previous calls is older than the current one ${latestHistoryId}, tagging the inbox")
          doTagInbox(latestHistoryId)
        case (Some(historyId), Some(latestHistoryId)) if historyId <= latestHistoryId =>
          Logger.info(s"historyId ${historyId} saved from previous calls and current one ${latestHistoryId} indicates that tagging the inbox can be skipped")
          fs(())
      }
    }
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
