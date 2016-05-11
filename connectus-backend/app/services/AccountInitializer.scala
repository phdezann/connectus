package services

import javax.inject.{Inject, Singleton}

import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleTokenResponse}
import common._
import services.AccountInitializer.TradeSuccess
import services.Repository.AuthorizationCodes

import scala.concurrent.{ExecutionContext, Future}

object AccountInitializer {
  case class TradeSuccess(email: Email, authorizationCodes: AuthorizationCodes, googleTokenResponse: GoogleTokenResponse)
  case class TradeFailure(email: Email)
}

@Singleton
class AccountInitializer @Inject()(implicit exec: ExecutionContext, googleAuthorization: GoogleAuthorization, androidIdVerifier: AndroidIdVerifier, repository: Repository) {

  def addUser(authorizationCodes: AuthorizationCodes) = {
    val action = trade(authorizationCodes)
    action.onFailure { case e => repository.onTradeFailure(authorizationCodes.authorizationCodeId, e) }
    action.flatMap(tradeSuccess => repository.initAccount(tradeSuccess).map(_ => tradeSuccess.email))
  }

  def trade(authorizationCodes: AuthorizationCodes): Future[TradeSuccess] = {
    val credsOpt: Option[(Email, GoogleIdToken)] = for {
      token <- androidIdVerifier.parse(authorizationCodes.androidId)
      email <- androidIdVerifier.checkAndroidId(token)
    } yield (email, token)
    val onError = ff(new IllegalStateException("Credentials validation failed"))
    credsOpt.fold[Future[TradeSuccess]](onError) { case (email, providedIdToken) =>
      googleAuthorization.convert(authorizationCodes.authorizationCode).filter { googleTokenResponse => {
        for {
          googleIdToken <- androidIdVerifier.parse(googleTokenResponse.getIdToken)
          valid <- androidIdVerifier.checkGoogleProvidedAndroidId(providedIdToken, googleIdToken)
        } yield valid
      }.fold(false)(identity)
      }.map {TradeSuccess(email, authorizationCodes, _)}
    }
  }
}
