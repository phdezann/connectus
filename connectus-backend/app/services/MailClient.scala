package services

import java.nio.charset.Charset
import java.time.{Instant, LocalDateTime, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeParseException
import javax.inject.{Inject, Singleton}

import com.google.api.services.gmail.model._
import common._
import model.{GmailAttachment, GmailAttachmentData, GmailLabel, GmailMessage, GmailThread, GmailWatchReply, InternetAddress}
import play.api.Logger

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MailClient @Inject()(implicit exec: ExecutionContext, gmailClient: GmailClient) {

  def listLabels(email: Email): Future[List[GmailLabel]] = {
    Logger.info(s"Listing all labels for $email")
    gmailClient.listLabels(email).map(_.map(label => LabelMapper(label)))
  }

  def createLabel(email: Email, labelName: String): Future[GmailLabel] = {
    Logger.info(s"Creation label with name $labelName for $email")
    gmailClient.createLabel(email, labelName).map(label => LabelMapper(label))
  }

  def addLabels(email: Email, query: String, labels: List[GmailLabel]): Future[Unit] = {
    Logger.info(s"Adding label $labels to threads from query '$query' for $email")
    if (labels.isEmpty) fs(()) else gmailClient.addLabels(email, query, labels.map(_.id)).map(_ => ())
  }

  def removeLabels(email: Email, query: String, labels: List[GmailLabel]): Future[Unit] = {
    Logger.info(s"Removing label $labels to threads from query '$query' for $email")
    if (labels.isEmpty) fs(()) else gmailClient.removeLabels(email, query, labels.map(_.id)).map(_ => ())
  }

  def deleteLabel(email: Email, label: GmailLabel) = {
    Logger.info(s"Deleting label $label for $email")
    gmailClient.deleteLabel(email, label.id)
  }

  def listThreads(email: Email, query: String): Future[List[GmailThread]] = {
    Logger.info(s"Listing threads from query '$query' for $email")
    gmailClient.listThreads(email, query).map(_.map(thread => ThreadMapper(thread)))
  }

  def listMessagesOfThread(email: Email, threadId: String, allLabels: List[GmailLabel]): Future[List[GmailMessage]] = {
    Logger.info(s"Listing messages of thread with id $threadId for $email")
    gmailClient.listMessagesOfThread(email, threadId).map(_.map(message => MessageMapper(message, allLabels)))
  }

  def getMessage(email: Email, messageId: String, allLabels: List[GmailLabel]): Future[GmailMessage] = {
    Logger.info(s"Getting message with id $messageId for $email")
    gmailClient.getMessage(email, messageId).map(message => MessageMapper(message, allLabels))
  }

  def watch(email: Email, labelIds: List[String]) = {
    Logger.info(s"Start watching for $email")
    gmailClient.watch(email, labelIds).map(response => WatchMapper(response))
  }

  def reply(email: Email, threadId: String, toAddress: String, personal: String, subject: String, content: String, allLabels: List[GmailLabel]) = {
    Logger.info(s"Reply to address $toAddress and threadId $threadId for $email")
    gmailClient.reply(email, threadId, toAddress, personal, subject, content)
  }

  def getLastHistoryId(email: Email, startHistoryId: BigInt): Future[BigInt] = {
    Logger.info(s"Getting last history with startHistoryId $startHistoryId for $email")
    gmailClient.getLastHistory(email, startHistoryId).map(_.getHistoryId)
  }

  def getAttachment(email: Email, messageId: String, attachmentId: String): Future[GmailAttachmentData] = {
    Logger.info(s"Getting attachment with messageId $messageId and attachmentId $attachmentId for $email")
    gmailClient.getAttachment(email, messageId, attachmentId).map(AttachmentMapper(_))
  }
}

object WatchMapper {
  def apply(watchResponse: WatchResponse): GmailWatchReply = {
    val expirationDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(watchResponse.getExpiration), ZoneOffset.UTC);
    val historyId = watchResponse.getHistoryId
    GmailWatchReply(expirationDate, historyId)
  }
}

object LabelMapper {
  def apply(label: Label): GmailLabel = new GmailLabel(label.getId, label.getName)
}

object ThreadMapper {
  def apply(thread: Thread): GmailThread = new GmailThread(thread.getId, thread.getSnippet, thread.getHistoryId)
}

object MessageMapper {

  def apply(message: Message, allLabels: List[GmailLabel]): GmailMessage = {
    val headers = message.getPayload.getHeaders.asScala.toList
    val dateOpt = getHeader(headers, "Date").flatMap(parseDate(_))
    val fromOpt = getHeader(headers, "From").map(parseHeader(_))
    val toOpt = getHeader(headers, "To").map(parseHeader(_))
    val subjectOpt = getHeader(headers, "Subject")
    val contentOpt = getContentAsText(message) map (_.trim)
    val historyId = message.getHistoryId
    val gmailLabels = message.getLabelIds.asScala.toList
      .map(labelId => allLabels.find(_.id == labelId)).flatten
      .map(label => GmailLabel(label.id, label.name))
    val attachments = getAttachments(message)
    val complete = dateOpt.isDefined && fromOpt.isDefined && subjectOpt.isDefined && contentOpt.isDefined
    GmailMessage(message.getId, dateOpt, fromOpt, toOpt, subjectOpt, contentOpt, historyId, gmailLabels, attachments, complete)
  }

  private def getHeader(headers: List[MessagePartHeader], headerNameIgnoreCase: String): Option[String] =
    headers.filter(h => h.getName.equalsIgnoreCase(headerNameIgnoreCase)).map(mph => mph.getValue).headOption

  def parseDate(date: String): Option[ZonedDateTime] = {
    def fixErrors(date: String) = date.replace(",  ", ", ")
    def parseWith(date: String, patterns: List[String]): Option[ZonedDateTime] = {
      patterns match {
        case head :: tail =>
          try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern(head)
            Some(ZonedDateTime.parse(date, formatter))
          } catch {
            case e: DateTimeParseException => parseWith(date, tail)
          }
        case _ => None
      }
    }
    parseWith(fixErrors(date),
      List(//
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss Z '('zzz')'",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z '('zzz')'"))
  }

  def parseHeader(from: String) = {
    val regex = """(.*)<(.*)>""".r
    from match {
      case regex(personal, address) => InternetAddress(address, Option(personal.trim))
      case _ => InternetAddress(from, None)
    }
  }

  private def scanParts(parts: List[MessagePart], mimeType: String): Option[MessagePart] = {
    def first(parts: List[MessagePart], mimeType: String): Option[MessagePart] =
      parts.find(p => p.getMimeType == mimeType)

    val childParts: List[MessagePart] = parts.foldLeft(List(): List[MessagePart])((acc, elt) => {
      Option(elt.getParts) match {
        case Some(childPart) => acc ++ childPart.asScala.toList
        case None => acc
      }
    })

    first(parts, mimeType) match {
      case textPart@Some(_) => textPart
      case None if childParts.nonEmpty => scanParts(childParts, mimeType)
      case _ => None
    }
  }

  private def getCharset(headers: List[MessagePartHeader], defaultCharset: String = "UTF-8"): Charset = {
    def getCharset(charsetIgnoringCase: String): Option[Charset] = {
      val availableCharsets: Map[String, Charset] = Charset.availableCharsets().asScala.toMap
      availableCharsets
        .find { case (name, c) => name.equalsIgnoreCase(charsetIgnoringCase) }
        .map { case (name, c) => c }
    }
    getHeader(headers, "Content-Type")
      .flatMap(getCharset(_))
      .fold(Charset.forName(defaultCharset))(identity)
  }

  private def getText(headers: List[MessagePartHeader], bytes: Option[Array[Byte]]): Option[String] =
    bytes.map(new Predef.String(_, getCharset(headers)))

  private def getContentAsText(message: Message): Option[String] = {
    message.getPayload.getMimeType match {
      case "text/plain" =>
        val headers = message.getPayload.getHeaders.asScala.toList
        val bytes = Option(message.getPayload.getBody.decodeData)
        getText(headers, bytes)
      case mimeType if mimeType.startsWith("multipart/") =>
        scanParts(parts(message), "text/plain") flatMap { part =>
          val headers = part.getHeaders.asScala.toList
          val bytes = Option(part.getBody.decodeData)
          getText(headers, bytes)
        }
      case _ => None
    }
  }

  def getAttachments(message: Message): List[GmailAttachment] = {
    // only keep parts with an attachmentId defined
    parts(message).filter(p => Option(p.getBody.getAttachmentId).isDefined).map(part => {
      val headers = part.getHeaders.asScala.map(header => (header.getName, header.getValue)).toMap
      GmailAttachment(part.getPartId, part.getMimeType, part.getFilename, part.getBody.getSize, part.getBody.getAttachmentId, headers)
    })
  }

  private def parts(message: Message): List[MessagePart] = {
    // message.getPayload.getParts can be null
    Option(message.getPayload.getParts).fold[List[MessagePart]](List())(_.asScala.toList)
  }
}

object AttachmentMapper {
  def apply(messagePartBody: MessagePartBody): GmailAttachmentData = {
    GmailAttachmentData(messagePartBody.getAttachmentId, messagePartBody.decodeData, messagePartBody.getSize)
  }
}
