package controllers

import javax.inject.Inject

import common._
import conf.AppConf
import model.{Notification, _}
import play.api.Logger
import play.api.mvc._
import services._

import scala.concurrent.ExecutionContext.Implicits.global

class AppController @Inject()(appConf: AppConf, gmailHookClient: GmailHookClient, userActorInitializer: ActorsInitializer) extends Controller {

  def index = Action {
    Ok(views.html.index(null))
  }

  def sync = Action.async(BodyParsers.parse.default) { request =>
    fs(Ok)
  }

  def gmail = {
    Action.async(BodyParsers.parse.json) { request =>
      val notificationResult = request.body.validate[Notification]
      Logger.info(s"Received gmail notification: $notificationResult")
      notificationResult.fold(errors => {
        Logger.error(errors.toString)
        fs(PreconditionFailed)
      }, notification => {
        gmailHookClient.parse(notification).onSuccess { case result => Logger.info(s"Result of tagging inbox after gmail notification $result") }
        fs(Ok)
      })
    }
  }
}
