package services

import com.google.api.services.gmail.model.Label
import common._
import model.{Contact, Resident}
import org.mockito.Mockito._
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import play.api.inject._
import play.api.inject.guice.GuiceInjectorBuilder

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MessageServiceTest extends FunSuiteLike with Mockito {

  test("buildContactQuery") {
    val contacts = List(Contact("contact1@provider.com", "roger"), Contact("contact2@provider.com", "roger"))
    assert(MessageService.newMessagesLabel(contacts) == "label:inbox -label:connectus (from:contact1@provider.com OR from:contact2@provider.com)")
  }

  test("buildContactQuery with empty contact list") {
    val contacts = List()
    assert(MessageService.newMessagesLabel(contacts) == "label:inbox -label:connectus label:no-contact")
  }

  test("label removed manually") {
    val gmailClient = mock[GmailClient]
    val firebaseFacade = mock[FirebaseFacade]
    val injector = new GuiceInjectorBuilder() //
      .overrides(bind[GmailClient].toInstance(gmailClient))
      .overrides(bind[FirebaseFacade].toInstance(firebaseFacade))
      .build

    val accountId = "me@gmail.com"
    val roger = Resident("1", "roger", "Roger", Some("Label_50"))
    val robert = Resident("2", "robert", "Robert", Some("Label_51"))
    val residents = Map(roger -> List(), robert -> List())

    when(firebaseFacade.getResidentsAndContacts(any)) thenReturn fs(residents)
    when(firebaseFacade.addResidentLabelId(any, any, any)) thenReturn fs(())
    when(firebaseFacade.saveMessages(any)) thenReturn fs(())

    when(gmailClient.listLabels(any)) thenReturn fs(List())
    when(gmailClient.createLabel(any, any)) thenReturn fs(new Label())
    when(gmailClient.addLabel(any, any, any)) thenReturn fs(List())
    when(gmailClient.listMessages(any, any)) thenReturn fs(List())

    val messageService = injector.instanceOf[MessageService]
    val resultFuture = messageService.tagInbox(accountId)
    Await.ready(resultFuture, Duration.Inf)

    verify(gmailClient).createLabel(accountId, MessageService.ConnectusLabelName)
    verify(gmailClient).createLabel(accountId, MessageService.toLabel(roger))
    verify(gmailClient).createLabel(accountId, MessageService.toLabel(robert))
  }
}
