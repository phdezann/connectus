package services

import java.time.LocalDateTime

import akka.actor._
import akka.pattern.pipe
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.services.gmail.GmailRequest

import scala.concurrent._

object GmailClientThrottlerActor {
  case class ScheduleGmailRequest(request: GmailRequest[_])
  case class ScheduleBatchRequest(request: BatchRequest)
}

class GoogleClientThrottlerActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case GmailClientThrottlerActor.ScheduleGmailRequest(request) =>
      val s = sender
      context.actorOf(Props(new GmailRequestExecutor(s, request)))
    case GmailClientThrottlerActor.ScheduleBatchRequest(request) =>
      val s = sender
      context.actorOf(Props(new BatchRequestExecutor(s, request)))
  }
}

abstract class AbstractGoogleClientExecutor(client: ActorRef) extends Actor with ActorLogging {
  implicit val executor = context.dispatcher
  pipe(Future {blocking {execute}}) to self

  def execute: Any

  override def receive: Receive = {
    case Status.Failure(f) =>
      client ! Status.Failure(f)
      context.stop(self)
    case response =>
      client ! response
      context.stop(self)
  }
}

class GmailRequestExecutor[T](client: ActorRef, request: GmailRequest[T]) extends AbstractGoogleClientExecutor(client) {
  override def execute: T = request.execute
}

class BatchRequestExecutor(client: ActorRef, request: BatchRequest) extends AbstractGoogleClientExecutor(client) {
  override def execute = request.execute
}
