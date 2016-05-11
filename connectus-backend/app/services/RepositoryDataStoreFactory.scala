package services

import java.util
import javax.inject.Inject

import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.util.store.DataStore
import services.support.{SCDataStore, SCRepositoryDataStoreFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

class RepositoryDataStoreFactory @Inject()(repository: Repository, implicit val exec: ExecutionContext) extends SCRepositoryDataStoreFactory {
  override def createStoredCredentialDataStore(id: String): DataStore[StoredCredential] = new SCDataStore(this, id) {
    // this method needs to be implemented when tokens are refreshed by Credential.refreshToken()
    override def set(key: String, value: StoredCredential): DataStore[StoredCredential] = {
      val credential = value.asInstanceOf[StoredCredential]
      val accessTokenOpt: Option[String] = Option(credential.getAccessToken)
      val millisecondsOpt: Option[Long] = Option(credential.getExpirationTimeMilliseconds).map(Long2long(_))
      val res = repository.updateAccessToken(key, accessTokenOpt, millisecondsOpt)
      // it is safe to block here as we are already inside a blocking block
      Await.result(res, Duration.Inf)
      this
    }
    override def get(key: String): StoredCredential = {
      val sc: Future[StoredCredential] = repository.getCredentials(key).map(credentials => //
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
