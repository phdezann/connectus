package support

import javax.inject.Inject

import play.api.Configuration

class AppConf @Inject()(configuration: Configuration) {
  def getGoogleClientSecret = configuration.getString("application.auth.google.client.secret").get
  def getRefreshToken = configuration.getString("application.auth.refresh.token").get
  def getGmailSubscription = configuration.getString("application.gmail.subscription").get
  def getFirebaseJwtToken = configuration.getString("application.firebase.jwt.token").get

}
