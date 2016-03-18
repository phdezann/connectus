package support

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.inject.AbstractModule
import controllers.AppController
import services.RepositoryDataStoreFactory

class AppModule extends AbstractModule {
  def configure() = {
    bind(classOf[AppController]).asEagerSingleton // in prod mode all dependencies of AppController get built at startup time
    bind(classOf[AbstractDataStoreFactory]).to(classOf[RepositoryDataStoreFactory])
    bind(classOf[GoogleIdTokenVerifier]).toInstance(new GoogleIdTokenVerifier.Builder(new NetHttpTransport, new JacksonFactory).build)
  }
}
