package services

import _root_.support.AppConf
import common._
import model._
import org.mockito.Mockito._
import play.api.inject._
import services.Repository.MessagesSnapshot
import support.TestBase

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RepositoryTest extends TestBase {

  val accountId = "me@gmail.com"
  val roger = Resident("1", "roger", "Roger", Some("Label_50"))
  val robert = Resident("2", "robert", "Robert", Some("Label_51"))
  val residentLabels = Map(roger -> GmailLabel("Label_50", "roger"), robert -> GmailLabel("Label_51", "robert"))
  val Thread1Id = "t1"
  val Thread2Id = "t2"
  val threadId1Message1Id = "t1m1"
  val threadId1Message2Id = "t1m2"
  val threadId2Message1Id = "t2m1"
  val thread1 = GmailThread(Thread1Id, "", 0)
  val thread2 = GmailThread(Thread2Id, "", 0)
  val firebaseUrl = "fakeUrl"

  var firebaseFutureWrappers: FirebaseFutureWrappers = _
  var repository: Repository = _

  before {
    val appConf = mock[AppConf]
    firebaseFutureWrappers = mock[FirebaseFutureWrappers]
    repository = getTestGuiceApplicationBuilder //
      .overrides(bind[FirebaseFutureWrappers].toInstance(firebaseFutureWrappers))
      .overrides(bind[AppConf].toInstance(appConf))
      .build.injector.instanceOf[Repository]

    when(appConf.getFirebaseUrl) thenReturn firebaseUrl
    when(firebaseFutureWrappers.updateChildrenFuture(any, any)) thenReturn fs(())
  }

  test("buildContactQuery") {
    val contacts = List(Contact("contact1@provider.com", "roger"), Contact("contact2@provider.com", "robert"))
    assert(LabelService.residentUntaggedMessages(contacts) == "label:inbox (from:contact1@provider.com OR from:contact2@provider.com)")
  }

  test("buildContactQuery with empty contact list") {
    assert(LabelService.residentUntaggedMessages(List()) == s"label:inbox label:${LabelService.AbsentLabelName}")
  }

  test("find deleted messages") {
    val messages = List(GmailMessage(threadId1Message2Id, None, None, None, None, None, 0, List(), true))
    val adminThreadIds = Map(Thread1Id -> List(threadId1Message1Id, threadId1Message2Id))
    val threadBundles = List(ThreadBundle(thread1, messages))

    val result = repository.findDeletedMessageIds(adminThreadIds, threadBundles)
    assert(result == Map(Thread1Id -> List(threadId1Message1Id)))
  }

  test("find deleted threads") {
    val messages = List(GmailMessage(threadId2Message1Id, None, None, None, None, None, 0, List(), true))
    val adminThreadIds = Map(Thread1Id -> List(threadId1Message1Id, threadId1Message2Id))
    val threadBundles = List(ThreadBundle(thread2, messages))

    val result = repository.findDeletedThreadIds(adminThreadIds, threadBundles)
    assert(result == List(Thread1Id))
  }

  test("save threads to empty database") {
    val message = GmailMessage(threadId2Message1Id, None, None, None, None, None, 0, List(), true)
    val threadBundles = List(ThreadBundle(thread2, List(message)))
    val messageSnapshot = MessagesSnapshot()

    val result = repository.saveThreads(accountId, threadBundles, messageSnapshot, residentLabels)
    Await.ready(result, Duration.Inf)

    val values = Map(
      s"messages/me@gmail,com/admin/inbox/t2/id" -> "t2",
      s"messages/me@gmail,com/admin/inbox/t2/snippet" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/date" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/from" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/subject" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/content" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/resident" -> null,
      s"messages/me@gmail,com/admin/threads/t2/t2m1/date" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/from" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/subject" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/content" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/resident" -> null)
    verify(firebaseFutureWrappers).updateChildrenFuture(firebaseUrl, values)
  }

  test("save threads to empty database with two residents") {
    val message1 = GmailMessage(threadId1Message1Id, None, None, None, None, None, 0, List(residentLabels(roger)), true)
    val message2 = GmailMessage(threadId2Message1Id, None, None, None, None, None, 0, List(residentLabels(robert)), true)
    val threadBundles = List(ThreadBundle(thread1, List(message1)), ThreadBundle(thread2, List(message2)))
    val messageSnapshot = MessagesSnapshot()

    val result = repository.saveThreads(accountId, threadBundles, messageSnapshot, residentLabels)
    Await.ready(result, Duration.Inf)

    val values = Map(
      s"messages/me@gmail,com/1/inbox/t1/id" -> "t1",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/content" -> "",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/date" -> "",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/from" -> "",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/labels/Label_50" -> "roger",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/resident/id" -> "1",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/resident/labelName" -> "Roger",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/resident/name" -> "roger",
      s"messages/me@gmail,com/1/inbox/t1/lastMessage/subject" -> "",
      s"messages/me@gmail,com/1/inbox/t1/snippet" -> "",
      s"messages/me@gmail,com/1/threads/t1/t1m1/content" -> "",
      s"messages/me@gmail,com/1/threads/t1/t1m1/date" -> "",
      s"messages/me@gmail,com/1/threads/t1/t1m1/from" -> "",
      s"messages/me@gmail,com/1/threads/t1/t1m1/labels/Label_50" -> "roger",
      s"messages/me@gmail,com/1/threads/t1/t1m1/resident/id" -> "1",
      s"messages/me@gmail,com/1/threads/t1/t1m1/resident/labelName" -> "Roger",
      s"messages/me@gmail,com/1/threads/t1/t1m1/resident/name" -> "roger",
      s"messages/me@gmail,com/1/threads/t1/t1m1/subject" -> "",
      s"messages/me@gmail,com/2/inbox/t2/id" -> "t2",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/content" -> "",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/date" -> "",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/from" -> "",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/labels/Label_51" -> "robert",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/resident/id" -> "2",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/resident/labelName" -> "Robert",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/resident/name" -> "robert",
      s"messages/me@gmail,com/2/inbox/t2/lastMessage/subject" -> "",
      s"messages/me@gmail,com/2/inbox/t2/snippet" -> "",
      s"messages/me@gmail,com/2/threads/t2/t2m1/content" -> "",
      s"messages/me@gmail,com/2/threads/t2/t2m1/date" -> "",
      s"messages/me@gmail,com/2/threads/t2/t2m1/from" -> "",
      s"messages/me@gmail,com/2/threads/t2/t2m1/labels/Label_51" -> "robert",
      s"messages/me@gmail,com/2/threads/t2/t2m1/resident/id" -> "2",
      s"messages/me@gmail,com/2/threads/t2/t2m1/resident/labelName" -> "Robert",
      s"messages/me@gmail,com/2/threads/t2/t2m1/resident/name" -> "robert",
      s"messages/me@gmail,com/2/threads/t2/t2m1/subject" -> "",
      s"messages/me@gmail,com/admin/inbox/t1/id" -> "t1",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/content" -> "",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/date" -> "",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/from" -> "",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/labels/Label_50" -> "roger",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/id" -> "1",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/labelName" -> "Roger",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/name" -> "roger",
      s"messages/me@gmail,com/admin/inbox/t1/lastMessage/subject" -> "",
      s"messages/me@gmail,com/admin/inbox/t1/snippet" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/id" -> "t2",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/content" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/date" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/from" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/labels/Label_51" -> "robert",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/id" -> "2",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/labelName" -> "Robert",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/name" -> "robert",
      s"messages/me@gmail,com/admin/inbox/t2/lastMessage/subject" -> "",
      s"messages/me@gmail,com/admin/inbox/t2/snippet" -> "",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/content" -> "",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/date" -> "",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/from" -> "",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/labels/Label_50" -> "roger",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/resident/id" -> "1",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/resident/labelName" -> "Roger",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/resident/name" -> "roger",
      s"messages/me@gmail,com/admin/threads/t1/t1m1/subject" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/content" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/date" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/from" -> "",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/labels/Label_51" -> "robert",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/resident/id" -> "2",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/resident/labelName" -> "Robert",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/resident/name" -> "robert",
      s"messages/me@gmail,com/admin/threads/t2/t2m1/subject" -> "")
    verify(firebaseFutureWrappers).updateChildrenFuture(firebaseUrl, values)
  }

  test("save threads with a removed thread") {
    val thread = GmailThread(Thread2Id, "", 0)
    val message = GmailMessage(threadId2Message1Id, None, None, None, None, None, 0, List(residentLabels(robert)), true)
    val threadBundles = List(ThreadBundle(thread, List(message)))
    val messageSnapshot = MessagesSnapshot(allThreadIds = Map[ThreadId, List[MessageId]](Thread1Id -> List(threadId1Message1Id)))

    val result = repository.saveThreads(accountId, threadBundles, messageSnapshot, residentLabels)
    Await.ready(result, Duration.Inf)

    val values = Map(
      "messages/me@gmail,com/1/inbox/t1" -> null,
      "messages/me@gmail,com/1/threads/t1" -> null,
      "messages/me@gmail,com/2/inbox/t1" -> null,
      "messages/me@gmail,com/2/inbox/t2/id" -> "t2",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/content" -> "",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/date" -> "",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/from" -> "",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/labels/Label_51" -> "robert",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/resident/id" -> "2",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/resident/labelName" -> "Robert",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/resident/name" -> "robert",
      "messages/me@gmail,com/2/inbox/t2/lastMessage/subject" -> "",
      "messages/me@gmail,com/2/inbox/t2/snippet" -> "",
      "messages/me@gmail,com/2/threads/t1" -> null,
      "messages/me@gmail,com/2/threads/t2/t2m1/content" -> "",
      "messages/me@gmail,com/2/threads/t2/t2m1/date" -> "",
      "messages/me@gmail,com/2/threads/t2/t2m1/from" -> "",
      "messages/me@gmail,com/2/threads/t2/t2m1/labels/Label_51" -> "robert",
      "messages/me@gmail,com/2/threads/t2/t2m1/resident/id" -> "2",
      "messages/me@gmail,com/2/threads/t2/t2m1/resident/labelName" -> "Robert",
      "messages/me@gmail,com/2/threads/t2/t2m1/resident/name" -> "robert",
      "messages/me@gmail,com/2/threads/t2/t2m1/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t1" -> null,
      "messages/me@gmail,com/admin/inbox/t2/id" -> "t2",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/content" -> "",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/date" -> "",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/from" -> "",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/labels/Label_51" -> "robert",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/id" -> "2",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/labelName" -> "Robert",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/resident/name" -> "robert",
      "messages/me@gmail,com/admin/inbox/t2/lastMessage/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t2/snippet" -> "",
      "messages/me@gmail,com/admin/threads/t1" -> null,
      "messages/me@gmail,com/admin/threads/t2/t2m1/content" -> "",
      "messages/me@gmail,com/admin/threads/t2/t2m1/date" -> "",
      "messages/me@gmail,com/admin/threads/t2/t2m1/from" -> "",
      "messages/me@gmail,com/admin/threads/t2/t2m1/labels/Label_51" -> "robert",
      "messages/me@gmail,com/admin/threads/t2/t2m1/resident/id" -> "2",
      "messages/me@gmail,com/admin/threads/t2/t2m1/resident/labelName" -> "Robert",
      "messages/me@gmail,com/admin/threads/t2/t2m1/resident/name" -> "robert",
      "messages/me@gmail,com/admin/threads/t2/t2m1/subject" -> "")
    verify(firebaseFutureWrappers).updateChildrenFuture(firebaseUrl, values)
  }

  test("save threads with a removed message") {
    val message = GmailMessage(threadId1Message2Id, None, None, None, None, None, 0, List(residentLabels(roger)), true)
    val threadBundles = List(ThreadBundle(thread1, List(message)))
    val messageSnapshot = MessagesSnapshot(allThreadIds = Map(Thread1Id -> List(threadId1Message1Id, threadId1Message2Id)))

    val result = repository.saveThreads(accountId, threadBundles, messageSnapshot, residentLabels)
    Await.ready(result, Duration.Inf)

    val values = Map(
      "messages/me@gmail,com/1/inbox/t1/id" -> "t1",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/content" -> "",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/date" -> "",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/from" -> "",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/labels/Label_50" -> "roger",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/resident/id" -> "1",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/resident/labelName" -> "Roger",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/resident/name" -> "roger",
      "messages/me@gmail,com/1/inbox/t1/lastMessage/subject" -> "",
      "messages/me@gmail,com/1/inbox/t1/snippet" -> "",
      "messages/me@gmail,com/1/threads/t1/t1m1" -> null,
      "messages/me@gmail,com/1/threads/t1/t1m2/content" -> "",
      "messages/me@gmail,com/1/threads/t1/t1m2/date" -> "",
      "messages/me@gmail,com/1/threads/t1/t1m2/from" -> "",
      "messages/me@gmail,com/1/threads/t1/t1m2/labels/Label_50" -> "roger",
      "messages/me@gmail,com/1/threads/t1/t1m2/resident/id" -> "1",
      "messages/me@gmail,com/1/threads/t1/t1m2/resident/labelName" -> "Roger",
      "messages/me@gmail,com/1/threads/t1/t1m2/resident/name" -> "roger",
      "messages/me@gmail,com/1/threads/t1/t1m2/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t1/id" -> "t1",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/content" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/date" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/from" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/labels/Label_50" -> "roger",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/id" -> "1",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/labelName" -> "Roger",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/name" -> "roger",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t1/snippet" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m1" -> null,
      "messages/me@gmail,com/admin/threads/t1/t1m2/content" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/date" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/from" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/labels/Label_50" -> "roger",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/id" -> "1",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/labelName" -> "Roger",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/name" -> "roger",
      "messages/me@gmail,com/admin/threads/t1/t1m2/subject" -> "")
    verify(firebaseFutureWrappers).updateChildrenFuture(firebaseUrl, values)
  }

  test("update labels") {
    val message = GmailMessage(threadId1Message2Id, None, None, None, None, None, 0, List(residentLabels(robert)), true)
    val threadBundles = List(ThreadBundle(thread1, List(message)))
    val messageSnapshot = MessagesSnapshot(messagesLabels = Map(threadId1Message2Id -> List(residentLabels(roger))))

    val result = repository.saveThreads(accountId, threadBundles, messageSnapshot, residentLabels)
    Await.ready(result, Duration.Inf)

    val values = Map(
      "messages/me@gmail,com/2/inbox/t1/id" -> "t1",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/content" -> "",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/date" -> "",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/from" -> "",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/labels/Label_50" -> null,
      "messages/me@gmail,com/2/inbox/t1/lastMessage/labels/Label_51" -> "robert",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/resident/id" -> "2",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/resident/labelName" -> "Robert",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/resident/name" -> "robert",
      "messages/me@gmail,com/2/inbox/t1/lastMessage/subject" -> "",
      "messages/me@gmail,com/2/inbox/t1/snippet" -> "",
      "messages/me@gmail,com/2/threads/t1/t1m2/content" -> "",
      "messages/me@gmail,com/2/threads/t1/t1m2/date" -> "",
      "messages/me@gmail,com/2/threads/t1/t1m2/from" -> "",
      "messages/me@gmail,com/2/threads/t1/t1m2/labels/Label_50" -> null,
      "messages/me@gmail,com/2/threads/t1/t1m2/labels/Label_51" -> "robert",
      "messages/me@gmail,com/2/threads/t1/t1m2/resident/id" -> "2",
      "messages/me@gmail,com/2/threads/t1/t1m2/resident/labelName" -> "Robert",
      "messages/me@gmail,com/2/threads/t1/t1m2/resident/name" -> "robert",
      "messages/me@gmail,com/2/threads/t1/t1m2/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t1/id" -> "t1",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/content" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/date" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/from" -> "",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/labels/Label_50" -> null,
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/labels/Label_51" -> "robert",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/id" -> "2",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/labelName" -> "Robert",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/resident/name" -> "robert",
      "messages/me@gmail,com/admin/inbox/t1/lastMessage/subject" -> "",
      "messages/me@gmail,com/admin/inbox/t1/snippet" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/content" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/date" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/from" -> "",
      "messages/me@gmail,com/admin/threads/t1/t1m2/labels/Label_50" -> null,
      "messages/me@gmail,com/admin/threads/t1/t1m2/labels/Label_51" -> "robert",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/id" -> "2",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/labelName" -> "Robert",
      "messages/me@gmail,com/admin/threads/t1/t1m2/resident/name" -> "robert",
      "messages/me@gmail,com/admin/threads/t1/t1m2/subject" -> "")
    verify(firebaseFutureWrappers).updateChildrenFuture(firebaseUrl, values)
  }
}
