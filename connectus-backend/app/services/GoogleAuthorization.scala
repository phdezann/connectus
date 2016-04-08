package services

import java.io.StringReader
import javax.inject.{Inject, Singleton}

import _root_.support.AppConf
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.openidconnect.IdTokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleAuthorizationCodeTokenRequest, GoogleClientSecrets, GoogleTokenResponse}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes._
import com.google.common.collect.Lists._
import common._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GoogleAuthorization {
  val ApplicationName = "Connectus"
  val Scopes = newArrayList(GMAIL_COMPOSE, GMAIL_MODIFY)
  val transport = new NetHttpTransport
  val factory = new JacksonFactory
}

@Singleton
class GoogleAuthorization @Inject()(appConf: AppConf, dataStoreFactory: AbstractDataStoreFactory) {

  private def loadSecrets: GoogleClientSecrets =
    GoogleClientSecrets.load(GoogleAuthorization.factory, new StringReader(appConf.getGoogleClientSecret))

  private lazy val flow: GoogleAuthorizationCodeFlow = new Builder(GoogleAuthorization.transport, GoogleAuthorization.factory, loadSecrets, GoogleAuthorization.Scopes) //
    .setDataStoreFactory(dataStoreFactory)
    .setAccessType("offline") // So we can get a refresh and access the protected service while the user is gone
    .setApprovalPrompt("auto").build

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

  def getService(accountId: String): Future[Gmail] =
    Future {concurrent.blocking {Option(flow.loadCredential(accountId))}}
      .flatMap(fromOption(_))
      .map(gmail(_))

  private def gmail(credential: Credential): Gmail =
    new Gmail.Builder(GoogleAuthorization.transport, GoogleAuthorization.factory, credential).setApplicationName(GoogleAuthorization.ApplicationName).build
}

