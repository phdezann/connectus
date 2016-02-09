package services

import java.io._
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util
import javax.inject.{Singleton, Inject}

import _root_.support.AppConf
import com.google.api.client.auth.oauth2._
import com.google.api.client.auth.openidconnect.IdTokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder
import com.google.api.client.googleapis.auth.oauth2._
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes._
import com.google.api.services.gmail.model._
import com.google.common.collect.Lists._
import common._
import model.{GmailMessage, InternetAddress}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Utils {
  val ApplicationName = "Connectus"
  val Scopes = newArrayList(GMAIL_COMPOSE, GMAIL_MODIFY)
  val transport = new NetHttpTransport
  val factory = new JacksonFactory
}

// TODO La génération d'un access token à partir d'un refresh token implique la présence du "secret"
@Singleton
class GoogleAuthorization @Inject()(appConf: AppConf, dataStoreFactory: AbstractDataStoreFactory) {

  def loadSecrets: GoogleClientSecrets =
    GoogleClientSecrets.load(Utils.factory, new StringReader(appConf.getGoogleClientSecret))

  val flow: GoogleAuthorizationCodeFlow = new Builder(Utils.transport, Utils.factory, loadSecrets, Utils.Scopes) //
    .setDataStoreFactory(dataStoreFactory)
    .setAccessType("offline") // So we can get a refresh and access the protected service while the user is gone
    .setApprovalPrompt("auto").build

  def credentials(accountId: String): Future[Option[Credential]] = Future {concurrent.blocking {Option(flow.loadCredential(accountId))}}

  def addCredentials(userId: String, refreshToken: String) = {
    val tokenResponse: IdTokenResponse = new IdTokenResponse
    tokenResponse.setRefreshToken(refreshToken)
    flow.createAndStoreCredential(tokenResponse, userId)
  }

  def convert(authorisationCode: String): Future[GoogleTokenResponse] = {
    val request: GoogleAuthorizationCodeTokenRequest = flow.newTokenRequest(authorisationCode)
    // as specified in https://developers.google.com/identity/protocols/CrossClientAuth, the redirect_uri argument must be equal to null
    request.set("redirect_uri", null)
    Future {concurrent.blocking {request.execute()}}
  }
}

class GmailClient @Inject()(appConf: AppConf, googleAuthorization: GoogleAuthorization) {

  private def getService(userId: String): Future[Gmail] =
    googleAuthorization.credentials(userId)
      .flatMap(fromOption(_))
      .map(gmail(_))

  private def gmail(credential: Credential): Gmail =
    new Gmail.Builder(Utils.transport, Utils.factory, credential).setApplicationName(Utils.ApplicationName).build

  type email = String

  def watch(userId: String): Future[WatchResponse] = {
    for {
      gmail <- getService(userId)
      watchResponse <- callWatch(gmail)
    } yield watchResponse
  }

  private def callWatch(gmail: Gmail): Future[WatchResponse] =
    Future {
      concurrent.blocking {
        val request = gmail.users.watch("me", new WatchRequest().setTopicName(appConf.getGmailTopic))
        request.execute
      }
    }

  def listLabels(userId: String): Future[List[Label]] =
    for {
      gmail <- getService(userId)
      label <- listLabels(gmail)
    } yield label

  private def listLabels(gmail: Gmail): Future[List[Label]] =
    Future {
      concurrent.blocking {
        val labels = gmail.users().labels().list("me").execute.getLabels
        Option(labels).fold[List[Label]](List())(_.asScala.toList)
      }
    }

  def createLabel(userId: String, labelName: String): Future[Label] =
    for {
      gmail <- getService(userId)
      label <- createLabel(gmail, labelName)
    } yield label

  private def createLabel(gmail: Gmail, labelName: String): Future[Label] =
    Future {
      concurrent.blocking {
        val label = new Label().setName(labelName).setLabelListVisibility("labelShow").setMessageListVisibility("show")
        gmail.users().labels().create("me", label).execute
      }
    }

  def addLabel(userId: String, query: String, label: Label): Future[List[Message]] =
    for {
      gmail <- getService(userId)
      partialMessages <- fetchMessages(gmail, query)
      messages <- addLabel(gmail, partialMessages, label)
    } yield messages

  private def addLabel(gmail: Gmail, partialMessages: List[Message], label: Label): Future[List[Message]] = {
    val modifyMessageRequest = new ModifyMessageRequest().setAddLabelIds(List(label.getId).asJava)
    val requests = partialMessages.map(pm => gmail.users().messages().modify("me", pm.getId, modifyMessageRequest))
    executeBatch(gmail, requests.toList)
  }

  def listMessagesNoCache(userId: String, query: String) = listMessages(userId, query)

  def listMessages(userId: String, query: String): Future[List[GmailMessage]] =
    for {
      gmail <- getService(userId)
      partialMessages <- fetchMessages(gmail, query)
      messages <- listMessages(gmail, partialMessages)
    } yield messages

  def getMessage(userId: String, messageId: String): Future[Option[GmailMessage]] =
    for {
      gmail <- getService(userId)
      message <- getMessage(gmail, messageId)
    } yield message

  private def getMessage(gmail: Gmail, messageId: String): Future[Option[GmailMessage]] =
    listMessages(gmail, List(new Message().setId(messageId))) map {_.headOption}

  private def listMessages(gmail: Gmail, partialMessages: List[Message]): Future[List[GmailMessage]] = {
    val messagesRequests = partialMessages.map(pm => gmail.users.messages.get("me", pm.getId))
    for {
      gmailMessages <- executeBatch(gmail, messagesRequests.toList)
      gmailLabels <- fetchLabels(gmail, gmailMessages)
      messages <- fs(Converter(gmailMessages, gmailLabels))
    } yield messages
  }

  private def fetchMessages(gmail: Gmail, query: String): Future[List[Message]] =
    Future {
      concurrent.blocking {
        val request = gmail.users.messages.list("me").setQ(query)
        val response: ListMessagesResponse = request.execute
        if (response.getResultSizeEstimate > 0) response.getMessages.asScala.toList else List()
      }
    }

  private def fetchLabels(gmail: Gmail, messages: List[Message]): Future[Map[String, Label]] = {
    val labelIds = messages.foldLeft(Set[String]())((acc, message) => acc ++ message.getLabelIds.asScala.toSet)
    val requests = labelIds.map(labelId => gmail.users.labels.get("me", labelId))
    val results = executeBatch(gmail, requests.toList)
    results.map(labels => labels.map { label => label.getId -> label }.toMap)
  }

  private def executeBatch[T](gmail: Gmail, requests: List[AbstractGoogleJsonClientRequest[T]]): Future[List[T]] = {
    if (requests.isEmpty) {
      fs(List.empty[T])
    } else {
      Future {
        concurrent.blocking {
          val batch: BatchRequest = gmail.batch()
          val results: ListBuffer[T] = ListBuffer()
          val errors: ListBuffer[GoogleJsonError] = ListBuffer()
          requests.foreach(_.queue(batch, new JsonBatchCallback[T] {
            override def onSuccess(label: T, responseHeaders: HttpHeaders): Unit = results += label
            override def onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders): Unit = errors += e
          }))
          batch.execute
          (results.toList, errors.toList)
        }
      } flatMap { case (results, errors) => fold(results, errors) }
    }
  }

  class GoogleJsonErrorException(val msg: String) extends Throwable(msg)

  private def fold[T](results: T, errors: List[GoogleJsonError]): Future[T] =
    if (errors.isEmpty) fs(results) else ff(new GoogleJsonErrorException(errors.mkString(", ")))


  object Converter {

    def apply(messages: List[Message], labels: Map[String, Label]): List[GmailMessage] = messages.map(Converter(_, labels))

    def apply(message: Message, labels: Map[String, Label]): GmailMessage = {
      val headers = message.getPayload.getHeaders.asScala.toList
      val dateOpt = getHeader(headers, "Date").flatMap(parseDate(_))
      val fromOpt = getHeader(headers, "From").map(parseHeader(_))
      val toOpt = getHeader(headers, "To").map(parseHeader(_))
      val subjectOpt = getHeader(headers, "Subject")
      val contentOpt = getContentAsText(message) map (_.trim)
      val historyId = new java.math.BigDecimal(message.getHistoryId)
      val labelIds = message.getLabelIds.asScala.toList
      val complete = dateOpt.isDefined && fromOpt.isDefined && subjectOpt.isDefined && contentOpt.isDefined
      GmailMessage(message.getId, dateOpt, fromOpt, toOpt, subjectOpt, contentOpt, historyId, message.getThreadId, labelIds, complete)
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
}
