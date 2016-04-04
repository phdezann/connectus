package services

import javax.inject.Inject

import _root_.support.AppConf
import com.firebase.client.Firebase
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.common.base.Throwables
import services.AccountInitializer.TradeSuccess
import services.FirebaseFacade._

import scala.collection.JavaConverters._

class Repository @Inject()(appConf: AppConf, firebaseFacade: FirebaseFacade) {

  def initAccount(tradeSuccess: TradeSuccess) = {
    val email = tradeSuccess.googleTokenResponse.parseIdToken().getPayload.getEmail
    def expirationTimeInMilliSeconds(expiresInSecondsFromNow: Long) = System.currentTimeMillis + expiresInSecondsFromNow * 1000
    val encodedEmail = Util.encode(email)
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/${tradeSuccess.authorizationCodes.authorizationCodeId}/$TradeLogPath/$CodePath" -> LoginCodeSuccess,
      s"$UsersPath/$encodedEmail/$RefreshTokenPath" -> tradeSuccess.googleTokenResponse.getRefreshToken,
      s"$UsersPath/$encodedEmail/$AccessTokenPath" -> tradeSuccess.googleTokenResponse.getAccessToken,
      s"$UsersPath/$encodedEmail/$ExpirationTimeMilliSecondsPath" -> Long.box(expirationTimeInMilliSeconds(tradeSuccess.googleTokenResponse.getExpiresInSeconds)))
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  def onTradeFailure(authorizationCodeId: String, e: Throwable) = {
    val values: Map[String, AnyRef] = Map(
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$CodePath" -> getCode(e),
      s"$AuthorizationCodesPath/$authorizationCodeId/$TradeLogPath/$MessagePath" -> Throwables.getStackTraceAsString(e))
    val ref = new Firebase(appConf.getFirebaseUrl)
    FutureWrappers.updateChildrenFuture(ref, values.asJava)
  }

  private def getCode(exception: Throwable) = {
    exception match {
      case tre: TokenResponseException if tre.getDetails.getError == "invalid_grant" => LoginCodeInvalidGrant
      case _ => LoginCodeFailure
    }
  }
}
