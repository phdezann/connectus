package services

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.ListThreadsResponse
import common._
import conf.AppConf
import org.mockito.Mockito._
import play.api.inject._
import services.support.TestBase

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class GmailClientTest extends TestBase {

  val accountId = "me@gmail.com"
  var gmailThrottlerClient: GmailThrottlerClient = _
  var gmailClient: GmailClient = _
  var request: Gmail#Users#Threads#List = _

  before {
    val appConf = mock[AppConf]
    val googleAuthorization = mock[GoogleAuthorization]
    gmailThrottlerClient = mock[GmailThrottlerClient]

    val injector = getTestGuiceApplicationBuilder
      .overrides(bind[AppConf].toInstance(appConf))
      .overrides(bind[GoogleAuthorization].toInstance(googleAuthorization))
      .overrides(bind[GmailThrottlerClient].toInstance(gmailThrottlerClient))
      .build.injector

    gmailClient = injector.instanceOf[GmailClient]
    request = mock[Gmail#Users#Threads#List]
  }

  test("listThreads with nextPageToken = null") {
    val r1t1 = new com.google.api.services.gmail.model.Thread().setId("r1t1")
    val r1t2 = new com.google.api.services.gmail.model.Thread().setId("r1t2")
    val ltr1 = new ListThreadsResponse().setThreads(List(r1t1, r1t2).asJava)

    when(gmailThrottlerClient.scheduleListThreads(any, any)) thenReturn fs(ltr1) thenReturn fs(ltr1)

    val resultFuture = gmailClient.foldListThreads(accountId, request)
    val result = Await.result(resultFuture, Duration.Inf)
    assert(result == List(r1t1, r1t2))
  }

  test("listThreads with nextPageToken != null") {
    val r1t1 = new com.google.api.services.gmail.model.Thread().setId("r1t1")
    val r1t2 = new com.google.api.services.gmail.model.Thread().setId("r1t2")
    val ltr1 = new ListThreadsResponse().setThreads(List(r1t1, r1t2).asJava)
    ltr1.setNextPageToken("fakeToken")

    val r2t3 = new com.google.api.services.gmail.model.Thread().setId("r2t3")
    val r2t4 = new com.google.api.services.gmail.model.Thread().setId("r2t4")
    val ltr2 = new ListThreadsResponse().setThreads(List(r2t3, r2t4).asJava)

    when(gmailThrottlerClient.scheduleListThreads(any, any)) thenReturn fs(ltr1) thenReturn fs(ltr2)

    val resultFuture = gmailClient.foldListThreads(accountId, request)
    val result = Await.result(resultFuture, Duration.Inf)
    assert(result == List(r1t1, r1t2, r2t3, r2t4))
  }
}
