package support

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import play.api.inject._
import play.api.{Configuration, Environment}
import services.RepositoryDataStoreFactory

class AppModule extends Module {
  def bindings(env: Environment, conf: Configuration) = Seq(
    bind[AbstractDataStoreFactory].to(classOf[RepositoryDataStoreFactory]),
    bind[GoogleIdTokenVerifier].to(new GoogleIdTokenVerifier.Builder(new NetHttpTransport, new JacksonFactory).build)
  )
}
