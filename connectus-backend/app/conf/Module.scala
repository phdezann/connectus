package conf

import java.time.{Clock, ZoneId}

import akka.actor.{Actor, ActorRef, Props}
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.google.inject.util.Providers
import controllers.AppController
import play.api.libs.concurrent.{Akka, AkkaGuiceSupport}
import services._
import services.support.SystemClock

import scala.reflect.ClassTag

class AppModule extends AbstractModule {
  def configure = {
    bind(classOf[AppController]).asEagerSingleton // in prod mode all dependencies of AppController get built at startup time
    bind(classOf[AbstractDataStoreFactory]).to(classOf[RepositoryDataStoreFactory])
    bind(classOf[GoogleIdTokenVerifier]).toInstance(new GoogleIdTokenVerifier.Builder(new NetHttpTransport, new JacksonFactory).build)
    bind(classOf[Clock]).toInstance(new SystemClock(ZoneId.systemDefault()))
  }
}

class AkkaModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActorNoEager[SuperSupervisorActor](SuperSupervisorActor.actorName)
    bindActorFactory[UserSupervisorActor, UserSupervisorActor.Factory]
  }

  private def bindActorNoEager[T <: Actor : ClassTag](name: String, props: Props => Props = identity): Unit = {
    binder.bind(classOf[ActorRef])
      .annotatedWith(Names.named(name))
      .toProvider(Providers.guicify(Akka.providerOf[T](name, props)))
  }
}
