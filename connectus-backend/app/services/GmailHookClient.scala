package services

import javax.inject.Inject

import common._
import conf.AppConf
import model.{GmailNotificationMessage, Notification}
import org.apache.commons.codec.binary.StringUtils
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GmailHookClient @Inject()(appConf: AppConf, actorsClient: ActorsClient, messageService: MessageService) {

  def scheduleTagInbox(notification: Notification) =
    parse(notification).flatMap(gmailNotificationMessage => {
      val email = gmailNotificationMessage.emailAddress
      actorsClient.scheduleOnUserJobQueue(email, messageService.tagInbox(email, gmailNotificationMessage.historyId))
    })

  private def parse(notification: Notification): Future[GmailNotificationMessage] = {
    if (notification.subscription != appConf.getGmailSubscription) {
      ff(s"Subscription ${notification.subscription} does not match the environment configuration, please check it.")
    } else {
      val base64Payload = notification.message.data
      val jsonPayload = StringUtils.newStringUtf8(org.apache.commons.codec.binary.Base64.decodeBase64(base64Payload))
      Json.fromJson[GmailNotificationMessage](Json.parse(jsonPayload)).fold(errors => {
        ff(s"Json payload cannot be parsed: ${errors.toString}")
      }, gmailMessage => fs(gmailMessage))
    }
  }
}
