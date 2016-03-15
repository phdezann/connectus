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
      bind[AbstractDataStoreFactory].toInstance(fileDataStoreFactory)).build

    val injector1: Injector = injector.injector
    val authorization: GoogleAuthorization = injector1.instanceOf[GoogleAuthorization]
    authorization.addCredentials("connectus777@gmail.com", "REFRESH_TOKEN")

    val gmailClient = injector1.instanceOf[GmailClient]
    val messages: List[GmailMessage] = Await.result(gmailClient.listMessages("connectus777@gmail.com", "label:inbox"), Duration.Inf)


    val firebaseRef = new Firebase("https://connectusnow.firebaseio.com")
    firebaseRef.authWithCustomToken("firebase_secret", new AuthResultHandler {
      override def onAuthenticated(authData: AuthData) = println("onAuthenticated " + authData)
      override def onAuthenticationError(firebaseError: FirebaseError): Unit = println("onAuthenticationError " + firebaseError)
    })

    messages.foreach(message => {
      val child: Firebase = firebaseRef.child("messages").child(message.id)
      child.child("subject").setValue(message.subject.get, new CompletionListener {
        override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
          println(firebaseError)
          println(firebase)
        }
      })
      child.child("content").setValue(message.content.get, new CompletionListener {
        override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
          println(firebaseError)
          println(firebase)
        }
      })
    })

    Thread.sleep(10000)
  }
}
