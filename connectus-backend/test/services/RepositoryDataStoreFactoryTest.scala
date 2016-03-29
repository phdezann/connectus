package services

import com.google.api.client.auth.oauth2.StoredCredential
import org.scalatest.FunSuiteLike
import org.specs2.mock.Mockito
import common._

class RepositoryDataStoreFactoryTest extends FunSuiteLike with Mockito {
  test("store credentials while refreshing access token") {
    val firebaseFacade = mock[FirebaseFacade]
    val credentialDataStore = new RepositoryDataStoreFactory(firebaseFacade).createStoredCredentialDataStore("id")
    val accountId = "me@gmail.com"

    val credential = new StoredCredential
    credential.setAccessToken(null)
    credential.setRefreshToken("fakeRefreshToken")
    credential.setExpirationTimeMilliseconds(null)

    firebaseFacade.updateAccessToken(any, any, any) returns fs(())
    credentialDataStore.set(accountId, credential)
    there was one(firebaseFacade).updateAccessToken(accountId, null, null)
  }
}
