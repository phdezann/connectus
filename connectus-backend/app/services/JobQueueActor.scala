package services

import java.time.LocalDateTime
import javax.inject.{Inject, Named}

import akka.actor.Status._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import services.JobQueueActor.{JobResult, QueuedJob, ScheduledJob}

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._

class JobQueueActorClient @Inject()(@Named("futureJobQueueActor") futureJobQueueActor: ActorRef) {
  implicit val timeout = Timeout(5 minutes)
  def schedule(job: () => Future[_]) = futureJobQueueActor ? ScheduledJob(job)
}

object JobQueueActor {
  case class ScheduledJob(job: () => Future[_])
  case class JobResult(status: Status, start: LocalDateTime, end: LocalDateTime)

  case class QueuedJob(client: ActorRef, job: ScheduledJob)
}

class JobQueueActor extends Actor with ActorLogging {

  var pendingQueue = Queue.empty[QueuedJob]
  override def receive: Receive = normal

  def normal: Receive = {
    case scheduledJob@ScheduledJob(_) =>
      context.actorOf(Props(new FutureExecutor(scheduledJob)))
      context.become(executing(sender))
  }

  def executing(client: ActorRef): Receive = {
    case jobResult@JobResult(_, _, _) =>
      client ! jobResult
      pendingQueue.iterator.foreach(queuedJob => self.!(queuedJob.job)(queuedJob.client))
      pendingQueue = Queue.empty[QueuedJob]
      context.become(normal)
    case scheduledJob@ScheduledJob(_) =>
      pendingQueue = pendingQueue :+ QueuedJob(sender, scheduledJob)
  }
}

class FutureExecutor(scheduledJob: ScheduledJob) extends Actor with ActorLogging {
  def now = LocalDateTime.now()

  implicit val executor = context.dispatcher
  val start = now

  pipe(scheduledJob.job()) to self

  override def receive: Receive = {
    case Status.Failure(f) =>
      context.parent ! JobResult(Failure(f), start, now)
      context.stop(self)
    case response =>
      context.parent ! JobResult(Success(response), start, now)
      context.stop(self)
  }
}
