package services

import java.util
import javax.inject.Inject

import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.util.store.DataStore
import services.support.{SCDataStore, SCRepositoryDataStoreFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class RepositoryDataStoreFactory @Inject()(firebaseFacade: FirebaseFacade) extends SCRepositoryDataStoreFactory {
  override def createStoredCredentialDataStore(id: String): DataStore[StoredCredential] = new SCDataStore(this, id) {
    // this method needs to be implemented when tokens are refreshed by Credential.refreshToken()
    override def set(key: String, value: StoredCredential): DataStore[StoredCredential] = {
      val credential = value.asInstanceOf[StoredCredential]
      val res = firebaseFacade.updateAccessToken(key, credential.getAccessToken, credential.getExpirationTimeMilliseconds)
      // it is safe to block here as we are already inside a blocking block
      Await.result(res, Duration.Inf)
      this
    }
    override def get(key: String): StoredCredential = {
      val sc: Future[StoredCredential] = firebaseFacade.getCredentials(key).map(credentials => //
        new StoredCredential() //
          .setAccessToken(credentials.accessToken) //
          .setRefreshToken(credentials.refreshToken) //
          .setExpirationTimeMilliseconds(credentials.expirationTimeInMilliSeconds))
      Await.ready(sc, Duration.Inf)
      sc.value.get.getOrElse(null)
    }
    override def values(): util.Collection[StoredCredential] = ???
    override def keySet(): util.Set[String] = ???
    override def clear(): DataStore[StoredCredential] = ???
    override def delete(key: String): DataStore[StoredCredential] = ???
  }
}
