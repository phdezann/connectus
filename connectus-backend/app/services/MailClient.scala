package services

import java.nio.charset.Charset
import java.time.{Instant, LocalDateTime, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeParseException
import javax.inject.Inject

import com.google.api.services.gmail.model._
import common.Email
import model.{GmailLabel, GmailMessage, GmailThread, GmailWatchReply, InternetAddress}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MailClient @Inject()(gmailClient: GmailClient) {

  def listLabels(email: Email): Future[List[GmailLabel]] =
    gmailClient.listLabels(email).map(_.map(label => LabelMapper(label)))

  def createLabel(email: Email, labelName: String): Future[GmailLabel] =
    gmailClient.createLabel(email, labelName).map(label => LabelMapper(label))

  def addLabels(email: Email, query: String, labelIds: List[String]): Future[Unit] =
    for {
      allLabels <- gmailClient.listLabels(email)
      messages <- gmailClient.addLabels(email, query, labelIds)
    } yield ()

  def deleteLabel(email: Email, labelId: String) =
    gmailClient.deleteLabel(email, labelId)

  def listThreads(email: Email, query: String) =
    gmailClient.listThreads(email, query).map(_.map(thread => ThreadMapper(thread)))

  def listMessagesOfThread(email: Email, threadId: String): Future[List[GmailMessage]] =
    for {
      allLabels <- gmailClient.listLabels(email)
      messages <- gmailClient.listMessagesOfThread(email, threadId)
    } yield messages.map(message => MessageMapper(message, allLabels))

  def listMessages(email: Email, query: String): Future[List[GmailMessage]] =
    for {
      allLabels <- gmailClient.listLabels(email)
      messages <- gmailClient.listMessages(email, query)
    } yield messages.map(message => MessageMapper(message, allLabels))

  def watch(email: Email) =
    gmailClient.watch(email).map(response => WatchMapper(response))

  def reply(email: Email, labels: List[GmailLabel], threadId: String, toAddress: String, personal: String, content: String) =
    for {
      allLabels <- gmailClient.listLabels(email)
      message <- gmailClient.reply(email, threadId, toAddress, personal, content)
      _ <- gmailClient.addLabels(email, List(message.getId), labels.map(_.id))
    } yield ()
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

  def apply(message: Message, allLabels: List[Label]): GmailMessage = {
    val headers = message.getPayload.getHeaders.asScala.toList
    val dateOpt = getHeader(headers, "Date").flatMap(parseDate(_))
    val fromOpt = getHeader(headers, "From").map(parseHeader(_))
    val toOpt = getHeader(headers, "To").map(parseHeader(_))
    val subjectOpt = getHeader(headers, "Subject")
    val contentOpt = getContentAsText(message) map (_.trim)
    val historyId = message.getHistoryId
    val gmailLabels = message.getLabelIds.asScala.toList
      .map(labelId => allLabels.find(_.getId() == labelId)).flatten
      .map(label => GmailLabel(label.getId, label.getName))
    val complete = dateOpt.isDefined && fromOpt.isDefined && subjectOpt.isDefined && contentOpt.isDefined
    GmailMessage(message.getId, dateOpt, fromOpt, toOpt, subjectOpt, contentOpt, historyId, gmailLabels, complete)
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

  private def parts(message: Message): List[MessagePart] = {
    // message.getPayload.getParts can be null
    Option(message.getPayload.getParts).fold[List[MessagePart]](List())(_.asScala.toList)
  }
}
