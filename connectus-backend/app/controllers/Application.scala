package controllers

import javax.inject.Inject

import _root_.support.AppConf
import common._
import model.{Notification, _}
import org.apache.commons.codec.binary.StringUtils
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import services._

import scala.concurrent.ExecutionContext.Implicits.global

class AppController @Inject()(appConf: AppConf, messageService: MessageService, jobQueueActorClient: JobQueueActorClient, userActorInitializer: UserActorInitializer) extends Controller {

  def index = Action {
    Ok(views.html.index(null))
  }

  def sync = Action.async(BodyParsers.parse.default) { request =>
    fs(Ok)
  }

  def gmail =
    Action.async(BodyParsers.parse.json) { request =>
      val notificationResult = request.body.validate[Notification]
      Logger.info(s"Received gmail notification: $notificationResult")
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
            val email = gmailMessage.emailAddress
            Logger.info(s"Initiating tagInbox for $email")
            jobQueueActorClient.schedule(email, messageService.tagInbox(email)).map(_ => Ok)
          })
        })
    }
}
