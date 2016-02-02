package support

import java.io.File

import com.google.api.client.util.store.{AbstractDataStoreFactory, FileDataStoreFactory}
import play.api.inject._
import play.api.{Configuration, Environment}

class AppModule extends Module {
  def bindings(env: Environment, conf: Configuration) = Seq(
    bind[AbstractDataStoreFactory].to(new FileDataStoreFactory(new File("/tmp/datastore")))
  )
}
