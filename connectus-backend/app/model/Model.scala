package model

import java.time.{LocalDateTime, ZonedDateTime}

import common.Email

case class GmailWatchReply(expirationDate: LocalDateTime, historyId: BigInt)
case class GmailLabel(id: String, name: String)
case class GmailThread(id: String, snippet: String, historyId: BigInt)
case class GmailMessage(id: String, date: Option[ZonedDateTime], from: Option[InternetAddress], to: Option[InternetAddress], subject: Option[String], content: Option[String], historyId: BigInt, labels: List[GmailLabel], complete: Boolean)
case class InternetAddress(address: String, personal: Option[String])
case class ThreadBundle(thread: GmailThread, messages: List[GmailMessage]) {
  def lastUntrashedMessage = messages.reverse.find(message => !message.labels.exists(_.id == "TRASH"))
}

// gmail's webhook
case class NotificationMessage(data: String, message_id: String)
case class Notification(message: NotificationMessage, subscription: String)
case class GmailNotificationMessage(historyId: Long, emailAddress: String)

case class Resident(id: String, name: String, labelName: String, labelId: Option[String])
case class Contact(email: Email, residentId: String)
case class OutboxMessage(id:String, residentId: String, threadId: String, to: String, personal: String, content: String)
