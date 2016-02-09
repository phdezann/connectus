package support

import javax.inject.Inject

import play.api.Configuration

class AppConf @Inject()(configuration: Configuration) {
  def getGoogleClientSecret = configuration.getString("application.auth.google.client.secret").get
  def getGmailTopic = configuration.getString("application.gmail.topic").get
  def getGmailSubscription = configuration.getString("application.gmail.subscription").get
  def getFirebaseUrl = configuration.getString("application.firebase.url").get
  def getFirebaseJwtToken = configuration.getString("application.firebase.jwt.token").get
  def getWebComponentClientId = configuration.getString("application.auth.google.component.web.clientid").get
  def getAndroidAppComponentClientId = configuration.getString("application.auth.google.component.androidapp.clientid").get
}
