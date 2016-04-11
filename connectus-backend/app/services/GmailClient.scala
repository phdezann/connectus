package services

import java.io._
import java.util.Properties
import javax.inject.{Inject, Singleton}
import javax.mail.Session
import javax.mail.internet.{InternetAddress, MimeMessage}

import conf.AppConf
import com.google.api.client.util.Base64
import com.google.api.services.gmail.model._
import com.google.api.services.gmail.{Gmail, GmailRequest}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.language.postfixOps

@Singleton
class GmailClient @Inject()(appConf: AppConf, googleAuthorization: GoogleAuthorization, gmailThrottlerClient: GmailThrottlerClient) {

  private def seq[R <: GmailRequest[T], T](requests: List[R], mapper: R => Future[T]): Future[List[T]] = {
    val results = requests.map(mapper(_))
    Future.sequence(results)
  }

  def watch(userId: String, labelIds: List[String]): Future[WatchResponse] = {
    for {
      gmail <- googleAuthorization.getService(userId)
      watchResponse <- callWatch(userId, gmail, labelIds)
    } yield watchResponse
  }

  private def callWatch(userId: String, gmail: Gmail, labelIds: List[String]): Future[WatchResponse] = {
    val request = gmail.users.watch(userId, new WatchRequest().setTopicName(appConf.getGmailTopic).setLabelIds(labelIds.asJava))
    gmailThrottlerClient.scheduleWatch(userId, request)
  }

  def listLabels(userId: String): Future[List[Label]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      label <- listLabels(userId, gmail)
    } yield label

  private def listLabels(userId: String, gmail: Gmail): Future[List[Label]] = {
    val request = gmail.users().labels().list(userId)
    gmailThrottlerClient.scheduleListLabels(userId, request).map { response =>
      val labels = response.getLabels
      Option(labels).fold[List[Label]](List())(_.asScala.toList)
    }
  }

  def createLabel(userId: String, labelName: String): Future[Label] =
    for {
      gmail <- googleAuthorization.getService(userId)
      label <- createLabel(userId, gmail, labelName)
    } yield label

  private def createLabel(userId: String, gmail: Gmail, labelName: String): Future[Label] = {
    val label = new Label().setName(labelName).setLabelListVisibility("labelShow").setMessageListVisibility("show")
    val request = gmail.users().labels().create(userId, label)
    gmailThrottlerClient.scheduleCreateLabel(userId, request)
  }

  def addLabels(userId: String, query: String, labelIds: List[String]): Future[List[Thread]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      threads <- listThreads(userId, query)
      messages <- addLabels(userId, gmail, threads, labelIds)
    } yield messages

  private def addLabels(userId: String, gmail: Gmail, threads: List[Thread], labelIds: List[String]): Future[List[Thread]] = {
    val modifyThreadRequest = new ModifyThreadRequest().setAddLabelIds(labelIds.asJava)
    val requests = threads.map(pm => gmail.users().threads().modify(userId, pm.getId, modifyThreadRequest))
    seq[Gmail#Users#Threads#Modify, Thread](requests, gmailThrottlerClient.scheduleModifyThread(userId, _))
  }

  def removeLabels(userId: String, query: String, labelIds: List[String]): Future[List[Thread]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      threads <- listThreads(userId, query)
      messages <- removeLabels(userId, gmail, threads, labelIds)
    } yield messages

  private def removeLabels(userId: String, gmail: Gmail, threads: List[Thread], labelIds: List[String]): Future[List[Thread]] = {
    val modifyThreadRequest = new ModifyThreadRequest().setRemoveLabelIds(labelIds.asJava)
    val requests = threads.map(pm => gmail.users().threads().modify(userId, pm.getId, modifyThreadRequest))
    seq[Gmail#Users#Threads#Modify, Thread](requests, gmailThrottlerClient.scheduleModifyThread(userId, _))
  }

  def deleteLabel(userId: String, labelId: String): Future[Unit] =
    for {
      gmail <- googleAuthorization.getService(userId)
      result <- deleteLabel(userId, gmail, labelId)
    } yield result

  private def deleteLabel(userId: String, gmail: Gmail, labelId: String): Future[Unit] = {
    val request = gmail.users().labels().delete(userId, labelId)
    gmailThrottlerClient.scheduleDeleteLabel(userId, request)
  }

  def listThreads(userId: String, query: String): Future[List[Thread]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      threads <- fetchThreads(userId, gmail, query)
    } yield threads

  private def fetchThreads(userId: String, gmail: Gmail, query: String): Future[List[Thread]] = {
    val request = gmail.users.threads.list(userId).setQ(query)
    gmailThrottlerClient.scheduleListThreads(userId, request).map(response => {
      // ListThreadsResponse.getThreads can be null
      val threadsOpt = Option(request.execute.getThreads)
      threadsOpt.map(_.asScala.toList).fold(List[Thread]())(identity)
    })
  }

  def listMessagesOfThread(userId: String, threadId: String): Future[List[Message]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      partialMessages <- fetchMessagesOfThread(userId, gmail, threadId)
      messages <- getMessages(userId, gmail, partialMessages)
    } yield messages

  private def fetchMessagesOfThread(userId: String, gmail: Gmail, threadId: String): Future[List[Message]] = {
    val request = gmail.users.threads.get(userId, threadId)
    gmailThrottlerClient.scheduleGetThread(userId, request).map(_.getMessages.asScala.toList)
  }

  def listMessages(userId: String, query: String): Future[List[Message]] =
    for {
      gmail <- googleAuthorization.getService(userId)
      partialMessages <- listMessages(userId, gmail, query)
      messages <- getMessages(userId, gmail, partialMessages)
    } yield messages

  private def getMessages(userId: String, gmail: Gmail, partialMessages: List[Message]): Future[List[Message]] = {
    val messagesRequests = partialMessages.map(pm => gmail.users.messages.get(userId, pm.getId))
    seq[Gmail#Users#Messages#Get, Message](messagesRequests, gmailThrottlerClient.scheduleGetMessage(userId, _))
  }

  private def getMessage(userId: String, gmail: Gmail, partialMessage: Message): Future[Message] = {
    val request = gmail.users.messages.get(userId, partialMessage.getId)
    gmailThrottlerClient.scheduleGetMessage(userId, request)
  }

  private def listMessages(userId: String, gmail: Gmail, query: String): Future[List[Message]] = {
    val request = gmail.users.messages.list(userId).setQ(query)
    gmailThrottlerClient.scheduleListMessages(userId, request).map { response =>
      if (response.getResultSizeEstimate > 0) response.getMessages.asScala.toList else List()
    }
  }

  def reply(userId: String, threadId: String, toAddress: String, personal: String, subject: String, content: String): Future[Message] =
    for {
      gmail <- googleAuthorization.getService(userId)
      partialMessage <- reply(userId, gmail, threadId, toAddress, personal, subject, content)
      message <- getMessage(userId, gmail, partialMessage)
    } yield message

  private def reply(userId: String, gmail: Gmail, threadId: String, toAddress: String, personal: String, subject: String, content: String): Future[Message] = {
    val message = buildMessage(userId, threadId, toAddress, personal, subject, content)
    val request = gmail.users.messages.send(userId, message)
    gmailThrottlerClient.scheduleSendMessage(userId, request)
  }

  private def buildMessage(userId: String, threadId: String, toAddress: String, personal: String, subject: String, content: String) = {
    val props = new Properties
    val session = Session.getDefaultInstance(props, null)
    val email = new MimeMessage(session)

    email.addHeader("Subject", subject)
    email.setSubject("Re: " + subject)
    email.setFrom(new InternetAddress(userId, personal))
    email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress))
    email.setText(content)

    toMessage(email).setThreadId(threadId)
  }

  private def toMessage(message: MimeMessage): Message = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream
    message.writeTo(bytes)
    val encodedEmail: String = Base64.encodeBase64URLSafeString(bytes.toByteArray)
    new Message().setRaw(encodedEmail)
  }

  def listHistory(userId: String, startHistoryId: BigInt): Future[ListHistoryResponse] =
    for {
      gmail <- googleAuthorization.getService(userId)
      result <- listHistory(userId, gmail, startHistoryId)
    } yield result

  private def listHistory(userId: String, gmail: Gmail, startHistoryId: BigInt): Future[ListHistoryResponse] = {
    val request = gmail.users.history().list(userId)
    request.setStartHistoryId(startHistoryId.bigInteger)
    gmailThrottlerClient.scheduleListHistory(userId, request)
  }

  def getAttachment(userId: String, messageId: String, attachmentId: String): Future[MessagePartBody] =
    for {
      gmail <- googleAuthorization.getService(userId)
      result <- getAttachment(userId, gmail, messageId, attachmentId)
    } yield result

  private def getAttachment(userId: String, gmail: Gmail, messageId: String, attachmentId: String): Future[MessagePartBody] = {
    val request = gmail.users.messages().attachments().get(userId, messageId, attachmentId)
    gmailThrottlerClient.scheduleGetMessageAttachment(userId, request)
  }
}
