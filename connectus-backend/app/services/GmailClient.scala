package services

import java.io._
import javax.inject.{Inject, Singleton}

import _root_.support.AppConf
import com.google.api.client.auth.oauth2._
import com.google.api.client.auth.openidconnect.IdTokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder
import com.google.api.client.googleapis.auth.oauth2._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.api.services.gmail.GmailScopes._
import com.google.api.services.gmail.model._
import com.google.api.services.gmail.{Gmail, GmailRequest}
import com.google.common.collect.Lists._
import common._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.language.postfixOps

object Utils {
  val ApplicationName = "Connectus"
  val Scopes = newArrayList(GMAIL_COMPOSE, GMAIL_MODIFY)
  val transport = new NetHttpTransport
  val factory = new JacksonFactory
}

// TODO La génération d'un access token à partir d'un refresh token implique la présence du "secret"
@Singleton
class GoogleAuthorization @Inject()(appConf: AppConf, dataStoreFactory: AbstractDataStoreFactory) {

  def loadSecrets: GoogleClientSecrets =
    GoogleClientSecrets.load(Utils.factory, new StringReader(appConf.getGoogleClientSecret))

  lazy val flow: GoogleAuthorizationCodeFlow = new Builder(Utils.transport, Utils.factory, loadSecrets, Utils.Scopes) //
    .setDataStoreFactory(dataStoreFactory)
    .setAccessType("offline") // So we can get a refresh and access the protected service while the user is gone
    .setApprovalPrompt("auto").build

  def credentials(accountId: String): Future[Option[Credential]] = Future {concurrent.blocking {Option(flow.loadCredential(accountId))}}

  def addCredentials(userId: String, refreshToken: String) = {
    val tokenResponse: IdTokenResponse = new IdTokenResponse
    tokenResponse.setRefreshToken(refreshToken)
    flow.createAndStoreCredential(tokenResponse, userId)
  }

  def convert(authorisationCode: String): Future[GoogleTokenResponse] = {
    val request: GoogleAuthorizationCodeTokenRequest = flow.newTokenRequest(authorisationCode)
    // as specified in https://developers.google.com/identity/protocols/CrossClientAuth, the redirect_uri argument must be equal to null
    request.set("redirect_uri", null)
    Future {concurrent.blocking {request.execute()}}
  }
}

@Singleton
class GmailClient @Inject()(appConf: AppConf, googleAuthorization: GoogleAuthorization, gmailThrottlerClient: GmailThrottlerClient) {

  private def getService(userId: String): Future[Gmail] =
    googleAuthorization.credentials(userId)
      .flatMap(fromOption(_))
      .map(gmail(_))

  private def gmail(credential: Credential): Gmail =
    new Gmail.Builder(Utils.transport, Utils.factory, credential).setApplicationName(Utils.ApplicationName).build

  private def seq[R <: GmailRequest[T], T](requests: List[R], mapper: R => Future[T]): Future[List[T]] = {
    val results = requests.map(mapper(_))
    Future.sequence(results)
  }

  def watch(userId: String): Future[WatchResponse] = {
    for {
      gmail <- getService(userId)
      watchResponse <- callWatch(userId, gmail)
    } yield watchResponse
  }

  private def callWatch(userId: String, gmail: Gmail): Future[WatchResponse] = {
    val request = gmail.users.watch("me", new WatchRequest().setTopicName(appConf.getGmailTopic))
    gmailThrottlerClient.scheduleWatch(userId, request)
  }

  def listLabels(userId: String): Future[List[Label]] =
    for {
      gmail <- getService(userId)
      label <- listLabels(userId, gmail)
    } yield label

  private def listLabels(userId: String, gmail: Gmail): Future[List[Label]] = {
    val request = gmail.users().labels().list("me")
    gmailThrottlerClient.scheduleListLabels(userId, request).map { response =>
      val labels = response.getLabels
      Option(labels).fold[List[Label]](List())(_.asScala.toList)
    }
  }

  def createLabel(userId: String, labelName: String): Future[Label] =
    for {
      gmail <- getService(userId)
      label <- createLabel(userId, gmail, labelName)
    } yield label

  private def createLabel(userId: String, gmail: Gmail, labelName: String): Future[Label] = {
    val label = new Label().setName(labelName).setLabelListVisibility("labelShow").setMessageListVisibility("show")
    val request = gmail.users().labels().create("me", label)
    gmailThrottlerClient.scheduleCreateLabel(userId, request)
  }

  def addLabels(userId: String, query: String, labelIds: List[String]): Future[List[Message]] =
    for {
      gmail <- getService(userId)
      partialMessages <- listMessages(userId, gmail, query)
      messages <- addLabels(userId, gmail, partialMessages, labelIds)
    } yield messages

  private def addLabels(userId: String, gmail: Gmail, partialMessages: List[Message], labelIds: List[String]): Future[List[Message]] = {
    val modifyMessageRequest = new ModifyMessageRequest().setAddLabelIds(labelIds.asJava)
    val requests = partialMessages.map(pm => gmail.users().messages().modify("me", pm.getId, modifyMessageRequest))
    seq[Gmail#Users#Messages#Modify, Message](requests, gmailThrottlerClient.scheduleModifyMessage(userId, _))
  }

  def deleteLabel(userId: String, labelId: String): Future[Unit] =
    for {
      gmail <- getService(userId)
      result <- deleteLabel(userId, gmail, labelId)
    } yield result

  private def deleteLabel(userId: String, gmail: Gmail, labelId: String): Future[Unit] = {
    val request = gmail.users().labels().delete("me", labelId)
    gmailThrottlerClient.scheduleDeleteLabel(userId, request)
  }

  def listThreads(userId: String, query: String): Future[List[Thread]] =
    for {
      gmail <- getService(userId)
      threads <- fetchThreads(userId, gmail, query)
    } yield threads

  private def fetchThreads(userId: String, gmail: Gmail, query: String): Future[List[Thread]] = {
    val request = gmail.users.threads.list("me").setQ(query)
    gmailThrottlerClient.scheduleListThreads(userId, request).map(response => {
      // ListThreadsResponse.getThreads can be null
      val threadsOpt = Option(request.execute.getThreads)
      threadsOpt.map(_.asScala.toList).fold(List[Thread]())(identity)
    })
  }

  def listMessagesOfThread(userId: String, threadId: String): Future[List[Message]] =
    for {
      gmail <- getService(userId)
      partialMessages <- fetchMessagesOfThread(userId, gmail, threadId)
      messages <- getMessages(userId, gmail, partialMessages)
    } yield messages

  private def fetchMessagesOfThread(userId: String, gmail: Gmail, threadId: String): Future[List[Message]] = {
    val request = gmail.users.threads.get("me", threadId)
    gmailThrottlerClient.scheduleGetThread(userId, request).map(_.getMessages.asScala.toList)
  }

  def listMessages(userId: String, query: String): Future[List[Message]] =
    for {
      gmail <- getService(userId)
      partialMessages <- listMessages(userId, gmail, query)
      messages <- getMessages(userId, gmail, partialMessages)
    } yield messages

  private def getMessages(userId: String, gmail: Gmail, partialMessages: List[Message]): Future[List[Message]] = {
    val messagesRequests = partialMessages.map(pm => gmail.users.messages.get("me", pm.getId))
    seq[Gmail#Users#Messages#Get, Message](messagesRequests, gmailThrottlerClient.scheduleGetMessage(userId, _))
  }

  private def listMessages(userId: String, gmail: Gmail, query: String): Future[List[Message]] = {
    val request = gmail.users.messages.list("me").setQ(query)
    gmailThrottlerClient.scheduleListMessages(userId, request).map { response =>
      if (response.getResultSizeEstimate > 0) response.getMessages.asScala.toList else List()
    }
  }
}
