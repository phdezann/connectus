package services.support

import akka.pattern.FutureTimeoutSupport
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfter, FunSuiteLike}
import org.specs2.mock.Mockito
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import services.EnvironmentHelper

trait TestBase extends FunSuiteLike with Mockito with BeforeAndAfter with FutureTimeoutSupport {
  def getTestGuiceApplicationBuilder = {
    val environmentHelper = mock[EnvironmentHelper]
    when(environmentHelper.isInTest) thenReturn true
    new GuiceApplicationBuilder().overrides(bind[EnvironmentHelper].toInstance(environmentHelper))
  }
}
