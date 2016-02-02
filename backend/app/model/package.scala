import play.api.libs.json.Json

package object model {
  lazy implicit val readsNotificationMessage = Json.reads[NotificationMessage]
  lazy implicit val readsNotification = Json.reads[Notification]
  lazy implicit val readsGmailNotificationMessage = Json.reads[GmailNotificationMessage]
}
