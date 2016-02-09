package org.connectus;

import com.firebase.client.*;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.connectus.support.NoOpObservable.noOp;

public class FirebaseFacade {

    @Inject
    public FirebaseFacade() {
    }

    @AllArgsConstructor
    public static class AccessToken {
        String value;
    }

    @AllArgsConstructor
    @ToString
    public static class TokenTradeReport {
        String code;
        Optional<String> message;
    }

    @AllArgsConstructor
    @Getter
    public static class ExpiredAuthorizationCodeException extends Throwable {
        TokenTradeReport tokenTradeReport;
        String authorizationCode;
    }

    @AllArgsConstructor
    @Getter
    public static class BulkException extends Throwable {
        TokenTradeReport tokenTradeReport;
    }

    public class FirebaseException extends Throwable {
        public FirebaseException(Throwable cause) {
            super(cause);
        }
    }

    public void addResident(String email, String name) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentsUrl(encode(email)));
        ref.push().child(FirebaseFacadeConstants.RESIDENT_NAME_PROPERTY).setValue(name);
    }

    public void addContact(String email, String residentId, String emailOfContact) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getContactsUrl(encode(email), residentId));
        ref.push().child(FirebaseFacadeConstants.CONTACT_EMAIL_PROPERTY).setValue(emailOfContact);
    }

    public Observable<NoOp> sendCredentials(LoginOrchestrator.LoginCredentials creds) {
        Firebase authorizationCodesUrl = new Firebase(FirebaseFacadeConstants.getAuthorizationCodesUrl());

        Map<String, Object> values = Maps.newHashMap();
        values.put(FirebaseFacadeConstants.ANDROID_ID_PATH, creds.getAndroidId());
        values.put(FirebaseFacadeConstants.AUTHORIZATION_CODE_PATH, creds.getAuthorizationCode());

        Firebase push = authorizationCodesUrl.push();
        String authorizationId = push.getKey();

        Firebase authorizationIdRef = new Firebase(FirebaseFacadeConstants.getAuthorizationIdUrl(authorizationId));
        Firebase reportRef = new Firebase(FirebaseFacadeConstants.getReportUrl(authorizationId));

        return updateChildren(push, values) // push AndroidId and AuthorizationCode to the server
                .flatMap(ignore -> readIndefinitely(reportRef, FirebaseFacadeConstants.AUTHORIZATION_CODE_SERVER_PROCESSING_TIMEOUT_IN_SECONDS)) //
                .flatMap(report -> cleanup(authorizationIdRef).map(noOp -> report)) //
                .flatMap(report -> checkErrors(creds, report)) //
                .map(ignore -> noOp());
    }

    private Observable<FirebaseFacade.TokenTradeReport> checkErrors(LoginOrchestrator.LoginCredentials creds, FirebaseFacade.TokenTradeReport tokenTradeReport) {
        if (StringUtils.equals(tokenTradeReport.code, FirebaseFacadeConstants.LOGIN_CODE_SUCCESS)) {
            return Observable.just(tokenTradeReport);
        } else if (StringUtils.equals(tokenTradeReport.code, FirebaseFacadeConstants.LOGIN_CODE_INVALID_GRANT)) {
            return Observable.error(new FirebaseFacade.ExpiredAuthorizationCodeException(tokenTradeReport, creds.getAuthorizationCode()));
        } else {
            return Observable.error(new FirebaseFacade.BulkException(tokenTradeReport));
        }
    }

    public Observable<Boolean> isRefreshTokenAvailable(String email) {
        Firebase refreshTokenRef = new Firebase(FirebaseFacadeConstants.getRefreshTokenUrl(encode(email)));
        return readOnce(refreshTokenRef).map(value -> value != null);
    }

    private Observable<NoOp> cleanup(Firebase ref) {
        ReplaySubject<NoOp> subject = ReplaySubject.create();
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

    public Observable<AuthData> loginWithGoogle(AccessToken token) {
        ReplaySubject<AuthData> subject = ReplaySubject.create();
        Firebase firebase = new Firebase(FirebaseFacadeConstants.getRootUrl());
        firebase.authWithOAuthToken(FirebaseFacadeConstants.OAUTH_GOOGLE_PROVIDER, token.value, new Firebase.AuthResultHandler() {

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

    private Observable<NoOp> updateChildren(Firebase ref, Map<String, Object> values) {
        PublishSubject<NoOp> subject = PublishSubject.create();
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

    private Observable<TokenTradeReport> readIndefinitely(Firebase ref, long timeoutInSeconds) {
        PublishSubject<TokenTradeReport> subject = PublishSubject.create();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object codeValue = dataSnapshot.child(FirebaseFacadeConstants.CODE_PATH).getValue();
                Object messageValue = dataSnapshot.child(FirebaseFacadeConstants.MESSAGE_PATH).getValue();
                if (codeValue != null) {
                    TokenTradeReport report = new TokenTradeReport((String) codeValue, Optional.fromNullable((String) messageValue));
                    subject.onNext(report);
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

    private Observable<String> readOnce(Firebase ref) {
        PublishSubject<String> subject = PublishSubject.create();
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                subject.onNext((String) dataSnapshot.getValue());
                subject.onCompleted();
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
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

    public static String encode(String email) {
        return email.replace('.', ',');
    }
}
