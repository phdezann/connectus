package services

import java.io.File

import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client.{AuthData, Firebase, FirebaseError}
import com.google.api.client.util.store.{AbstractDataStoreFactory, FileDataStoreFactory}
import model.GmailMessage
import org.scalatest.FunSuite
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class GmailClientTest extends FunSuite {

  ignore("draft") {
    val fileDataStoreFactory: FileDataStoreFactory = new FileDataStoreFactory(new File("/tmp/datastore"))
    val injector = new GuiceApplicationBuilder().overrides(
      bind[AbstractDataStoreFactory].toInstance(fileDataStoreFactory)).build.injector

    val authorization: GoogleAuthorization = injector.instanceOf[GoogleAuthorization]
    authorization.addCredentials("connectus777@gmail.com", "REFRESH_TOKEN")

    val gmailClient = injector.instanceOf[GmailClient]
  }
}
