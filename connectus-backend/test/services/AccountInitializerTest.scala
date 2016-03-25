package services

import _root_.support.AppConf
import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleIdTokenVerifier, GoogleTokenResponse}
import common._
import org.mockito.Mockito._
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import play.api.inject._
import play.api.inject.guice.GuiceInjectorBuilder

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class AccountInitializerTest extends FunSuiteLike with Mockito {

  test("successful token acquisition") {
    val resultFuture: Future[(Email, GoogleTokenResponse)] = testTrade(asString("account1-android-id"), asString("account1-google-id-token"))
    val result: (Email, GoogleTokenResponse) = Await.result(resultFuture, Duration.Inf)
    assert(result._1.endsWith("@gmail.com"))
  }

  test("androidId check fails") {
    val resultFuture: Future[(Email, GoogleTokenResponse)] = testTrade(asString("account2-android-id"), asString("account1-google-id-token"), webComponentClientId = "fake.apps.googleusercontent.com")
    Await.ready(resultFuture, Duration.Inf)
    assert(resultFuture.value.get.failed.get.getMessage == "Credentials validation failed")
  }

  test("googleIdToken check fails") {
    val resultFuture: Future[(Email, GoogleTokenResponse)] = testTrade(asString("account2-android-id"), asString("account1-google-id-token"))
    Await.ready(resultFuture, Duration.Inf)
    assert(resultFuture.value.get.failed.get.getMessage == "Future.filter predicate is not satisfied")
  }

  private def testTrade(androidId: String, googleIdToken: String, webComponentClientId: String = "962110749658-k24d3n9nh9tjtqrjgpaq5rr9omhn7kbe.apps.googleusercontent.com") = {
    val response = new GoogleTokenResponse()
    response.setAccessToken("accessToken")
    response.setRefreshToken("refreshToken")
    response.setExpiresInSeconds(3600l)
    response.setIdToken(googleIdToken)

    val firebaseFacade = mock[FirebaseFacade]
    val googleIdTokenVerifier = mock[GoogleIdTokenVerifier]
    val googleAuthorization = mock[GoogleAuthorization]
    val appConf = mock[AppConf]
    val messageService = mock[MessageService]
    val jobQueueActorClient = mock[JobQueueActorClient]

    when(googleIdTokenVerifier.verify(any[GoogleIdToken])) thenReturn true
    when(appConf.getWebComponentClientId) thenReturn webComponentClientId
    when(appConf.getAndroidAppComponentClientId) thenReturn "962110749658-f3rmklm7clp0jsokdf2mfi83s11sra2r.apps.googleusercontent.com"
    when(googleAuthorization.convert(any[String])) thenReturn fs(response)

    val injector = new GuiceInjectorBuilder()
      .overrides(bind[GoogleIdTokenVerifier].toInstance(googleIdTokenVerifier))
      .overrides(bind[FirebaseFacade].toInstance(firebaseFacade))
      .overrides(bind[AppConf].toInstance(appConf))
      .overrides(bind[GoogleAuthorization].toInstance(googleAuthorization))
      .overrides(bind[MessageService].toInstance(messageService))
      .overrides(bind[JobQueueActorClient].toInstance(jobQueueActorClient))
      .build

    injector.instanceOf[AccountInitializer].trade(androidId, "authorizationCode")
  }
}
