package services

import common._
import model._
import org.mockito.Mockito._
import play.api.inject._
import services.support.TestBase

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MessageServiceTest extends TestBase {

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

  val mailClient = mock[MailClient]
  val repository = mock[Repository]
  val injector = getTestGuiceApplicationBuilder //
    .overrides(bind[MailClient].toInstance(mailClient))
    .overrides(bind[Repository].toInstance(repository))
    .build.injector

  test("buildContactQuery") {
    val contacts = List(Contact("contact1@provider.com", "roger"), Contact("contact2@provider.com", "robert"))
    assert(LabelService.residentUntaggedMessages(contacts) == "label:inbox -label:connectus (from:contact1@provider.com OR from:contact2@provider.com)")
  }

  test("buildContactQuery with empty contact list") {
    assert(LabelService.residentUntaggedMessages(List()) == "label:inbox -label:connectus label:no-contact")
  }

  test("label removed manually") {
    val contacts = Map(roger -> List(), robert -> List())

    when(repository.getResidentsAndContacts(any)) thenReturn fs(contacts)
    when(repository.addResidentLabelId(any, any, any)) thenReturn fs(())
    when(repository.saveMessages(any)) thenReturn fs(())

    when(mailClient.listLabels(any)) thenReturn fs(List())
    when(mailClient.createLabel(any, any)) thenReturn fs(new GmailLabel("id", LabelService.ConnectusLabelName))
    when(mailClient.addLabels(any, any, any)) thenReturn fs(())
    when(mailClient.listMessages(any, any)) thenReturn fs(List())

    val messageService = injector.instanceOf[MessageService]
    val result = messageService.tagInbox(accountId)
    Await.ready(result, Duration.Inf)

    verify(mailClient).createLabel(accountId, LabelService.ConnectusLabelName)
    verify(mailClient).createLabel(accountId, LabelService.toLabelName(roger))
    verify(mailClient).createLabel(accountId, LabelService.toLabelName(robert))
  }
}
