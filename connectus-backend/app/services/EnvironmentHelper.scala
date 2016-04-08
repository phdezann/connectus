package services

import javax.inject.Singleton

@Singleton
class EnvironmentHelper {
  def isInTest = false
  def listenersEnabled = true
}
