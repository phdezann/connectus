package services

import javax.inject.Inject

import _root_.support.AppConf
import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleIdTokenVerifier}
import com.google.api.client.json.jackson2.JacksonFactory
import play.api.Logger

import scala.util.Try

/**
 * https://developers.google.com/identity/protocols/CrossClientAuth
 */
class AndroidIdVerifier @Inject()(appConf: AppConf, googleIdTokenVerifier: GoogleIdTokenVerifier) {

  private val factory = new JacksonFactory

  /**
   * "iss: always accounts.google.com"
   */
  private def isIssuerValid(token: GoogleIdToken) = token.getPayload.getIssuer == "accounts.google.com"

  /**
   * "aud: the client ID of the web component of the project"
   */
  private def isAudienceValid(token: GoogleIdToken) = token.getPayload.getAudience == appConf.getWebComponentClientId

  /**
   * "azp: the client ID of the Android app component of project"
   */
  private def isAuthorizedPartyValid(token: GoogleIdToken) = {
    val check = token.getPayload.getAuthorizedParty == appConf.getAndroidAppComponentClientId
    if (!check) Logger.warn(s"AuthorizedParty ${ token.getPayload.getAuthorizedParty } does not match the environment configuration, please check it.")
    check
  }

  /**
   * "Verify that the sub field in the ID token from Google is identical to the sub field in the ID token that it received from the client."
   */
  private def hasSameSubject(clientToken: GoogleIdToken, googleToken: GoogleIdToken) = clientToken.getPayload.getSubject == googleToken.getPayload.getSubject

  /**
   * Validate the cryptographic signature of an Android app provided token
   */
  private def verify(token: GoogleIdToken) = googleIdTokenVerifier.verify(token)

  def parse(androidId: String): Option[GoogleIdToken] = {
    Try(GoogleIdToken.parse(factory, androidId)).toOption
  }

  def checkAndroidId(clientToken: GoogleIdToken): Option[String] = {
    if (verify(clientToken) && isIssuerValid(clientToken) && isAudienceValid(clientToken) && isAuthorizedPartyValid(clientToken)) {
      Some(clientToken.getPayload.getEmail)
    } else {
      None
    }
  }

  def checkGoogleProvidedAndroidId(clientToken: GoogleIdToken, googleToken: GoogleIdToken) = {
    Some(isAudienceValid(googleToken) && hasSameSubject(clientToken, googleToken))
  }
}
