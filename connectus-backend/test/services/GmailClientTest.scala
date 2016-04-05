package services

import java.io.File

import com.google.api.client.util.store.{AbstractDataStoreFactory, FileDataStoreFactory}
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import services.support.TestBase

class GmailClientTest extends TestBase {

  ignore("draft") {
    val fileDataStoreFactory: FileDataStoreFactory = new FileDataStoreFactory(new File("/tmp/datastore"))
    val injector = new GuiceApplicationBuilder().overrides(
      bind[AbstractDataStoreFactory].toInstance(fileDataStoreFactory)).build.injector

    val authorization: GoogleAuthorization = injector.instanceOf[GoogleAuthorization]
    authorization.addCredentials("connectus777@gmail.com", "REFRESH_TOKEN")

    val gmailClient = injector.instanceOf[GmailClient]
  }
}
