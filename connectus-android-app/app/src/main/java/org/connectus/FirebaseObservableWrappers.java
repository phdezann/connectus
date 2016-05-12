package org.connectus;

import com.firebase.client.*;
import com.google.common.base.Optional;
import org.connectus.support.NoOpObservable;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.connectus.support.NoOpObservable.noOp;

public class FirebaseObservableWrappers {

    @Inject
    public FirebaseObservableWrappers() {
    }

    public static class FirebaseException extends Throwable {
        public FirebaseException(Throwable cause) {
            super(cause);
        }
    }

    public Observable<AuthData> authWithOAuthToken(Firebase ref, String provider, String token) {
        ReplaySubject<AuthData> subject = ReplaySubject.create();
        ref.authWithOAuthToken(provider, token, new Firebase.AuthResultHandler() {

            @Override
            public void onAuthenticated(AuthData authData) {
                subject.onNext(authData);
                subject.onCompleted();
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
            }
        });
        return hopToIoScheduler(subject);
    }

    public Observable<DataSnapshot> read(Firebase ref) {
        ReplaySubject<DataSnapshot> subject = ReplaySubject.create();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                subject.onNext(dataSnapshot);
                subject.onCompleted();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
            }
        };
        ref.addListenerForSingleValueEvent(listener);
        return hopToIoScheduler(subject) //
                .finallyDo(() -> ref.removeEventListener(listener));
    }

    public Observable<DataSnapshot> listen(Firebase ref) {
        ReplaySubject<DataSnapshot> subject = ReplaySubject.create();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                subject.onNext(dataSnapshot);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
            }
        };
        ref.addValueEventListener(listener);
        return hopToIoScheduler(subject) //
                .doOnUnsubscribe(() -> ref.removeEventListener(listener));
    }

    public <T> Observable<T> listen(Firebase ref, Func1<DataSnapshot, Optional<T>> filter, long timeoutInSeconds) {
        ReplaySubject<T> subject = ReplaySubject.create();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Optional<T> value = filter.call(dataSnapshot);
                if (value.isPresent()) {
                    subject.onNext(value.get());
                    subject.onCompleted();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
            }
        };
        ref.addValueEventListener(listener);
        return hopToIoScheduler(subject) //
                .finallyDo(() -> ref.removeEventListener(listener)) //
                .timeout(timeoutInSeconds, TimeUnit.SECONDS);
    }

    public Observable<NoOpObservable.NoOp> updateChildren(Firebase ref, Map<String, Object> values) {
        PublishSubject<NoOpObservable.NoOp> subject = PublishSubject.create();
        ref.updateChildren(values, (firebaseError, firebase) -> {
            if (firebaseError != null) {
                subject.onError(new FirebaseException(firebaseError.toException()));
            } else {
                subject.onNext(noOp());
            }
            subject.onCompleted();
        });
        return hopToIoScheduler(subject);
    }

    public Observable<NoOpObservable.NoOp> clear(Firebase ref) {
        ReplaySubject<NoOpObservable.NoOp> subject = ReplaySubject.create();
        ref.removeValue((firebaseError, firebase) -> {
            if (firebaseError == null) {
                subject.onNext(noOp());
                subject.onCompleted();
            } else {
                subject.onError(firebaseError.toException());
            }
        });
        return hopToIoScheduler(subject);
    }

    /**
     * The Firebase SDK uses the Android main thread for its callbacks therefore we need to hop to another thread to perform the downstream reactive chain.
     */
    private <T> Observable<T> hopToIoScheduler(Observable<T> obs) {
        return obs.observeOn(Schedulers.io());
    }
}
