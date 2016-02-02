package model

import java.time.ZonedDateTime

case class GmailMessage(id: String, date: Option[ZonedDateTime], from: Option[InternetAddress], to: Option[InternetAddress], subject: Option[String], content: Option[String], historyId: BigDecimal, threadId: String, labelNames: List[String], complete: Boolean)
case class InternetAddress(address: String, personal: Option[String])

// gmail's webhook
case class NotificationMessage(data: String, message_id: String)
case class Notification(message: NotificationMessage, subscription: String)
case class GmailNotificationMessage(historyId: Long, emailAddress: String)
