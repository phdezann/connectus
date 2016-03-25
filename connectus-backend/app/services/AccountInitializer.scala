package services

import javax.inject.{Inject, Singleton}

import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleTokenResponse}
import common._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class AccountInitializer @Inject()(googleAuthorization: GoogleAuthorization, androidIdVerifier: AndroidIdVerifier, fireBaseFacade: FirebaseFacade, messageService: MessageService, jobQueueActorClient: JobQueueActorClient) {

  fireBaseFacade.listenAuthorizationCodes(authorizationCode => {
    def notTradedYet = authorizationCode.tradeCode.isEmpty
    if (notTradedYet) {
      jobQueueActorClient.schedule(() => {
        val op = {
          for {
            (email, googleTokenResponse) <- trade(authorizationCode.androidId, authorizationCode.authorizationCode)
            _ <- fireBaseFacade.initAccount(authorizationCode.authorizationCodeId, email, googleTokenResponse)
            _ <- messageService.tagInbox(email)
          } yield email
        }
        op.onComplete {
          case Success(email) =>
            Logger.info(s"Success at initializing user $email")
          case Failure(e) =>
            Logger.info("Failed to initialize user", e)
        }
        op.recoverWith { case e => fireBaseFacade.onTradeFailure(authorizationCode.authorizationCodeId, e) }
        op
      })
    }
  })

  def trade(androidId: String, authorizationCode: String): Future[(Email, GoogleTokenResponse)] = {
    val credsOpt: Option[(Email, GoogleIdToken)] = for {
      token <- androidIdVerifier.parse(androidId)
      email <- androidIdVerifier.checkAndroidId(token)
    } yield (email, token)
    val onError = ff(new IllegalStateException("Credentials validation failed"))
    credsOpt.fold[Future[(Email, GoogleTokenResponse)]](onError) { case (email, providedIdToken) =>
      googleAuthorization.convert(authorizationCode).filter { googleTokenResponse => {
        for {
          googleIdToken <- androidIdVerifier.parse(googleTokenResponse.getIdToken)
          valid <- androidIdVerifier.checkGoogleProvidedAndroidId(providedIdToken, googleIdToken)
        } yield valid
      }.fold(false)(identity)
      }.map {(email, _)}
    }
  }
}
