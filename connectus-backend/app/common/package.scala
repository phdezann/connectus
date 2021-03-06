import scala.concurrent.Future
import scala.concurrent.Promise

package object common {
  def fs[T](t: T) = Future.successful(t)
  def ff[T](e: Throwable) = Future.failed(e)
  def ff[T](illegalStateExceptionMsg: String): Future[T] = ff(new IllegalStateException(illegalStateExceptionMsg))
  def fromOption[T](option: Option[T]): Future[T] = {
    val promise: Promise[T] = Promise[T]
    if (option.isDefined) {
      promise.success(option.get)
    } else {
      promise.failure(new NoSuchElementException)
    }
    promise.future
  }
  type Email = String
  type ThreadId = String
  type MessageId = String
}
