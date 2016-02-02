package controllers

import javax.inject.Inject

import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client.{AuthData, Firebase, FirebaseError}
import common._
import model.{GmailMessage, Notification}
import org.apache.commons.codec.binary.StringUtils
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import services.{GoogleAuthorization, GmailClient}
import support.AppConf

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import model._

class AppController @Inject()(appConf: AppConf, gmailClient: GmailClient, googleAuthorization: GoogleAuthorization) extends Controller {

  def index = Action {
    Ok(views.html.index(null))
  }

  def sync = Action {
    dumpToFB
    Ok
  }

  def dumpToFB: Unit = {
    val firebaseRef = new Firebase("https://connectusnow.firebaseio.com")
    firebaseRef.authWithCustomToken(appConf.getFirebaseJwtToken, new AuthResultHandler {
      override def onAuthenticated(authData: AuthData) = println("onAuthenticated " + authData)
      override def onAuthenticationError(firebaseError: FirebaseError): Unit = println("onAuthenticationError " + firebaseError)
    })

    firebaseRef.child("messages").removeValue()
    googleAuthorization.addCredentials("connectus777@gmail.com", appConf.getRefreshToken)
    val messages: Future[List[GmailMessage]] = gmailClient.listMessages("connectus777@gmail.com", "label:inbox")
    messages.onSuccess { case messages => messages.foreach { message => {
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
    }
    }
    }
    messages.onFailure { case e => e.printStackTrace() }
  }

  def gmail =
    Action.async(BodyParsers.parse.json) { request =>
      val notificationResult = request.body.validate[Notification]
      notificationResult.fold(errors => {
        Logger.error(errors.toString)
        fs(PreconditionFailed)
      }, notification =>
        if (notification.subscription != appConf.getGmailSubscription) {
          fs(PreconditionFailed)
        } else {
          val base64Payload = notification.message.data
          val jsonPayload = StringUtils.newStringUtf8(org.apache.commons.codec.binary.Base64.decodeBase64(base64Payload))
          Json.fromJson[GmailNotificationMessage](Json.parse(jsonPayload)).fold(errors => {
            Logger.error(errors.toString)
            fs(PreconditionFailed)
          }, gmailMessage => {
            dumpToFB
            fs(Ok)
          })
        })
    }
}
