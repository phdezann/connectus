package services

import javax.inject.{Inject, Singleton}

import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client.{Firebase, _}

import scala.concurrent.Promise
import scala.collection.JavaConverters._

trait FirebaseCancellable {
  def cancel: Unit
}

@Singleton
class FirebaseFutureWrappers @Inject()(environmentHelper: EnvironmentHelper) {

  def connect(url: String, jwtToken: String) = {
    val promise = Promise[AuthData]
    new Firebase(url).authWithCustomToken(jwtToken, new AuthResultHandler {
      override def onAuthenticated(authData: AuthData) = promise.success(authData)
      override def onAuthenticationError(firebaseError: FirebaseError) = promise.failure(firebaseError.toException)
    })
    promise.future
  }

  def updateChildrenFuture(url: String, values: Map[String, AnyRef]) = {
    val promise = Promise[Unit]
    new Firebase(url).updateChildren(values.asJava, new CompletionListener {
      override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
        if (firebaseError == null) {
          promise.success(())
        } else {
          promise.failure(firebaseError.toException)
        }
      }
    })
    promise.future
  }

  def setValueFuture(url: String, value: AnyRef) = {
    val promise = Promise[Unit]
    new Firebase(url).setValue(value, new CompletionListener {
      override def onComplete(firebaseError: FirebaseError, firebase: Firebase) = {
        if (firebaseError == null) {
          promise.success(())
        } else {
          promise.failure(firebaseError.toException)
        }
      }
    })
    promise.future
  }

  def getValueFuture(url: String) = {
    val promise = Promise[DataSnapshot]
    new Firebase(url).addListenerForSingleValueEvent(new ValueEventListener() {
      override def onDataChange(dataSnapshot: DataSnapshot) = promise.success(dataSnapshot)
      override def onCancelled(firebaseError: FirebaseError) = promise.failure(firebaseError.toException)
    })
    promise.future
  }

  def listenChildEvent(url: String, onChildAddedCallback: DataSnapshot => Unit, onChildRemovedCallback: DataSnapshot => Unit = _ => (), onChildChangedCallback: DataSnapshot => Unit = _ => ()): FirebaseCancellable = {
    val ref = new Firebase(url)

    val listener: ChildEventListener = ref.addChildEventListener(new ChildEventListener {
      override def onChildAdded(snapshot: DataSnapshot, previousChildName: String) = if (environmentHelper.listenersEnabled) onChildAddedCallback(snapshot)
      override def onChildRemoved(snapshot: DataSnapshot) = if (environmentHelper.listenersEnabled) onChildRemovedCallback(snapshot)
      override def onChildMoved(snapshot: DataSnapshot, previousChildName: String) = {}
      override def onChildChanged(snapshot: DataSnapshot, previousChildName: String) = onChildChangedCallback(snapshot)
      override def onCancelled(error: FirebaseError) = {}
    })

    new FirebaseCancellable {
      def cancel = {
        ref.removeEventListener(listener)
      }
    }
  }
}

