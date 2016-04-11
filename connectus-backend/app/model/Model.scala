package model

import java.time.{LocalDateTime, ZonedDateTime}

import common.Email
import services.LabelService

case class GmailWatchReply(expirationDate: LocalDateTime, historyId: BigInt)
case class GmailLabel(id: String, name: String)
case class GmailThread(id: String, snippet: String, historyId: BigInt)
case class GmailMessage(id: String, date: Option[ZonedDateTime], from: Option[InternetAddress], to: Option[InternetAddress], subject: Option[String], content: Option[String], historyId: BigInt, labels: List[GmailLabel], attachments: List[GmailAttachment], complete: Boolean)
case class GmailAttachment(partId: String, mimeType: String, filename: String, bodySize: Int, bodyAttachmentId: String, headers: Map[String, String])
case class GmailAttachmentData(attachmentId: String, data: Array[Byte], size: Int)
case class InternetAddress(address: String, personal: Option[String])
case class ThreadBundle(thread: GmailThread, messages: List[GmailMessage]) {
  def lastUntrashedMessage = messages.reverse.find(message => !message.labels.exists(_.id == LabelService.TrashedLabelName))
  def contactEmail(email: Email): List[String] = messages
    .filter(message => message.from.fold(false)(_.address != email) && message.to.fold(false)(_.address == email))
    .map(_.from.get.address)
}

// gmail's webhook
case class NotificationMessage(data: String, message_id: String)
case class Notification(message: NotificationMessage, subscription: String)
case class GmailNotificationMessage(historyId: Long, emailAddress: String)

case class Resident(id: String, name: String, labelName: String, labelId: Option[String])
case class Contact(email: Email, residentId: String)
case class OutboxMessage(id: String, residentId: String, threadId: String, to: String, personal: String, subject: String, content: String)
