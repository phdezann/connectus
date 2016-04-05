package support

import java.time.{Clock, ZoneId}

import akka.actor.Actor
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.AbstractDataStoreFactory
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import controllers.AppController
import play.api.libs.concurrent.AkkaGuiceSupport
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

  def bindActorFactory[A: ClassTag, FactoryClass: ClassTag]: Unit = {
    install(new FactoryModuleBuilder()
      .implement(classOf[Actor], implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[_ <: Actor]])
      .build(implicitly[ClassTag[FactoryClass]].runtimeClass))
  }
}

class AkkaModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[UserActors](UserActors.actorName)
    bindActor[GmailWatcherActor](GmailWatcherActor.actorName)
    bindActor[GmailThrottlerActor](GmailThrottlerActor.actorName)
    bindActor[JobQueueActor](JobQueueActor.actorName)
    bindActor[ContactActor](ContactActor.actorName)
    bindActorFactory[ResidentListenerActor, ResidentListenerActor.Factory]
    bindActorFactory[ContactListenerActor, ContactListenerActor.Factory]
    bindActorFactory[ResidentActor, ResidentActor.Factory]
  }
}

