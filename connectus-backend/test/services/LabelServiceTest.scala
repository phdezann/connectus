package services

import common._
import model._
import org.mockito.Mockito._
import play.api.inject._
import services.support.TestBase

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class LabelServiceTest extends TestBase {

  val accountId = "me@gmail.com"

  var mailClient: MailClient = _
  var repository: Repository = _
  var labelService: LabelService = _

  before {
    mailClient = mock[MailClient]
    repository = mock[Repository]
    labelService = getTestGuiceApplicationBuilder //
      .overrides(bind[MailClient].toInstance(mailClient))
      .overrides(bind[Repository].toInstance(repository))
      .build.injector.instanceOf[LabelService]

    when(repository.addResidentLabelId(any, any, any)) thenReturn fs(())
  }

  test("new resident with no tag associated") {
    val roger = Resident("resident_1", "roger", "Roger", None)
    val rogerLabel = new GmailLabel("label_1", LabelService.toLabelName(roger))

    when(mailClient.listLabels(accountId)) thenReturn fs(List())
    when(mailClient.createLabel(accountId, rogerLabel.name)) thenReturn fs(rogerLabel)

    val result = labelService.syncResidentLabels(accountId, List(roger))
    val residents = Await.result(result, Duration.Inf)

    assert(residents == Map(roger.copy(labelId = Some(rogerLabel.id)) -> rogerLabel))
    verify(mailClient).createLabel(accountId, LabelService.toLabelName(roger))
  }

  test("resident with tag associated and new resident with no tag associated") {
    val roger = Resident("resident_1", "roger", "Roger", Some("label_1"))
    val rogerLabel = new GmailLabel("label_1", LabelService.toLabelName(roger))

    val robert = Resident("resident_2", "robert", "Robert", None)
    val robertLabel = new GmailLabel("label_2", LabelService.toLabelName(robert))

    when(mailClient.listLabels(accountId)) thenReturn fs(List(rogerLabel))
    when(mailClient.createLabel(accountId, robertLabel.name)) thenReturn fs(robertLabel)

    val result = labelService.syncResidentLabels(accountId, List(roger, robert))
    val residents = Await.result(result, Duration.Inf)

    assert(residents == Map(
      roger -> rogerLabel,
      robert.copy(labelId = Some(robertLabel.id)) -> robertLabel))
    verify(mailClient).createLabel(any, any)
  }

  test("resident with previously tag associated but now deleted") {
    val roger = Resident("resident_1", "roger", "Roger", Some("label_1"))
    val rogerLabel = new GmailLabel("label_2", LabelService.toLabelName(roger))

    when(mailClient.listLabels(accountId)) thenReturn fs(List())
    when(mailClient.createLabel(accountId, rogerLabel.name)) thenReturn fs(rogerLabel)

    val result = labelService.syncResidentLabels(accountId, List(roger))
    val residents = Await.result(result, Duration.Inf)

    assert(residents == Map(roger.copy(labelId = Some(rogerLabel.id)) -> rogerLabel))
    verify(mailClient).createLabel(any, any)
  }

  test("new resident with tag associated already present") {
    val roger = Resident("resident_1", "roger", "Roger", None)
    val rogerLabel = new GmailLabel("label_1", LabelService.toLabelName(roger))

    when(mailClient.listLabels(accountId)) thenReturn fs(List(rogerLabel))
    when(mailClient.createLabel(accountId, rogerLabel.name)) thenReturn fs(rogerLabel)

    val result = labelService.syncResidentLabels(accountId, List(roger))
    val residents = Await.result(result, Duration.Inf)

    assert(residents == Map(roger.copy(labelId = Some(rogerLabel.id)) -> rogerLabel))
    verify(mailClient, never()).createLabel(any, any)
  }

  test("remove unassociated tags") {
    val roger = Resident("resident_1", "roger", "Roger", Some("label_1"))
    val rogerLabel = new GmailLabel("label_1", LabelService.toLabelName(roger))
    val danglingLabel = new GmailLabel("label_2", s"${LabelService.ConnectusLabelName}/Dangling Label")

    when(mailClient.listLabels(accountId)) thenReturn fs(List(rogerLabel, danglingLabel))
    when(mailClient.deleteLabel(accountId, danglingLabel.id)) thenReturn fs(())

    val result = labelService.removeDanglingLabels(accountId, Map(roger -> rogerLabel))
    Await.ready(result, Duration.Inf)
    verify(mailClient).deleteLabel(any, any)
  }
}
