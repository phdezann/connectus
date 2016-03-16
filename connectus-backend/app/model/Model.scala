package model

import java.time.ZonedDateTime

import common.Email

case class GmailLabel(id: String, name: String)
case class GmailMessage(id: String, date: Option[ZonedDateTime], from: Option[InternetAddress], to: Option[InternetAddress], subject: Option[String], content: Option[String], historyId: BigDecimal, threadId: String, labels: List[GmailLabel], complete: Boolean)
case class InternetAddress(address: String, personal: Option[String])

// gmail's webhook
case class NotificationMessage(data: String, message_id: String)
case class Notification(message: NotificationMessage, subscription: String)
case class GmailNotificationMessage(historyId: Long, emailAddress: String)

case class Resident(id: String, name: String, labelId: Option[String])
case class Contact(email: Email, residentId: String)
