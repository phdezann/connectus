package controllers

import javax.inject.{Inject, Singleton}

import common._
import conf.AppConf
import model.{Notification, _}
import play.api.Logger
import play.api.mvc._
import services._

import scala.concurrent.ExecutionContext

@Singleton
class AppController @Inject()(implicit exec: ExecutionContext, appConf: AppConf, gmailHookClient: GmailHookClient) extends Controller {

  if (appConf.getMaintenanceMode) {
    // https://devcenter.heroku.com/articles/error-pages#customize-pages
    Logger.info("This app serves static resources when the main instance goes offline")
  }

  def index = Action {
    Ok(views.html.index())
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
        if (appConf.getMaintenanceMode) {
          Logger.error("Gmail notification should not be received by this app")
        } else {
          gmailHookClient.scheduleTagInbox(notification).onSuccess { case result => Logger.info(s"Result of tagging inbox after gmail notification $result") }
        }
        fs(Ok)
      })
    }
  }

  def maintenance = Action {
    Ok(views.html.maintenance())
  }
}
