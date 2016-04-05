package services

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.pipe
import services.JobQueueActor.{Job, QueuedJob}

import scala.collection.immutable.Queue
import scala.concurrent.Future

object JobQueueActor {
  final val actorName = "jobQueueActor"
  case class Job(payload: () => Future[_])
  case class QueuedJob(client: ActorRef, job: Job)
}

class JobQueueActor extends Actor with ActorLogging {

  var pendingQueue = Queue.empty[QueuedJob]
  override def receive: Receive = normal

  def normal: Receive = {
    case JobQueueActor.Job(job) =>
      context.actorOf(Props(new FutureExecutor(job)))
      context.become(executing(sender))
  }

  def executing(client: ActorRef): Receive = {
    case scheduledJob@Job(_) =>
      pendingQueue = pendingQueue :+ QueuedJob(sender, scheduledJob)
    case Status.Failure(cause) =>
      client ! scala.util.Failure(cause)
      resume(client)
    case result =>
      client ! scala.util.Success(result)
      resume(client)
  }

  private def resume(client: ActorRef) = {
    pendingQueue.iterator.foreach(queuedJob => self.!(queuedJob.job)(queuedJob.client))
    pendingQueue = Queue.empty[QueuedJob]
    context.become(normal)
  }
}

class FutureExecutor(payload: () => Future[_]) extends Actor with ActorLogging {
  def now = LocalDateTime.now()

  implicit val executor = context.dispatcher
  val start = now

  pipe(payload()) to self

  override def receive: Receive = {
    case f@Status.Failure(_) =>
      context.parent ! f
      context.stop(self)
    case result =>
      context.parent ! result
      context.stop(self)
  }
}
