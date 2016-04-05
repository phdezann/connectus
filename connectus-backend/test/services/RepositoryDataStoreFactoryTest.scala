package services

import com.google.api.client.auth.oauth2.StoredCredential
import common._
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito

class RepositoryDataStoreFactoryTest extends FunSuiteLike with Mockito {
  test("store credentials while refreshing access token") {
    val repository = mock[Repository]
    val credentialDataStore = new RepositoryDataStoreFactory(repository).createStoredCredentialDataStore("id")
    val accountId = "me@gmail.com"

    val credential = new StoredCredential
    credential.setAccessToken(null)
    credential.setRefreshToken("fakeRefreshToken")
    credential.setExpirationTimeMilliseconds(null)

    repository.updateAccessToken(any, any, any) returns fs(())
    credentialDataStore.set(accountId, credential)
    there was one(repository).updateAccessToken(accountId, null, null)
  }
}
