package org.connectus;

import com.firebase.client.*;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.AttachmentHttpRequest;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import javax.inject.Inject;
import java.util.List;
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

    public void addResident(String email, String name, String labelName) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentsUrl(encode(email)));
        Map<String, Object> values = Maps.newHashMap();
        values.put(FirebaseFacadeConstants.RESIDENT_NAME_PROPERTY, name);
        values.put(FirebaseFacadeConstants.RESIDENT_LABEL_NAME_PROPERTY, labelName);
        ref.push().updateChildren(values);
    }

    public void updateContact(String email, String residentId, String emailOfContact, Optional<String> previousResidentIdOpt) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getContactsUrl(encode(email)));
        Map<String, Object> values = Maps.newHashMap();
        if (previousResidentIdOpt.isPresent()) {
            String previousResidentId = previousResidentIdOpt.get();
            if (!previousResidentId.equals(residentId)) {
                values.put(residentId + "/" + FirebaseFacade.encode(emailOfContact), "Active");
                values.put(previousResidentId + "/" + FirebaseFacade.encode(emailOfContact), null);
            } else {
                values.put(residentId + "/" + FirebaseFacade.encode(emailOfContact), null);
            }
        } else {
            values.put(residentId + "/" + FirebaseFacade.encode(emailOfContact), "Active");
        }
        ref.updateChildren(values);
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
                .flatMap(ignore -> waitForTokenTradeReport(reportRef, FirebaseFacadeConstants.SERVER_PROCESSING_TIMEOUT_IN_SECONDS)) //
                .flatMap(report -> cleanup(authorizationIdRef).map(noOp -> report)) //
                .flatMap(report -> checkErrors(creds, report)) //
                .map(ignore -> noOp());
    }

    public Observable<NoOp> addOutboxMessage(String email, String residentId, String to, String threadId, String personal, String subject, String content) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getOutboxUrl(email));

        Map<String, Object> values = Maps.newHashMap();
        values.put("residentId", residentId);
        values.put("to", to);
        values.put("threadId", threadId);
        values.put("personal", personal);
        values.put("subject", subject);
        values.put("content", content);

        Firebase newOutboxMessage = ref.push();
        return updateChildren(newOutboxMessage, values);
    }

    public Observable<List<AttachmentHttpRequest>> getAttachmentRequests(String email, String messageId) {
        Firebase ref = new Firebase(FirebaseFacadeConstants.getAttachmentRequestUrl(email));

        Map<String, Object> values = Maps.newHashMap();
        values.put(messageId, "Active");

        Firebase requestRef = ref.child("requests");
        Firebase responseRef = ref.child("responses");

        return updateChildren(requestRef, values) //
                .flatMap(ignore -> waitForAttachmentRequests(responseRef, FirebaseFacadeConstants.SERVER_PROCESSING_TIMEOUT_IN_SECONDS)) //
                .flatMap(response -> cleanup(requestRef).flatMap(noOp -> cleanup(responseRef)) //
                        .map(noOp -> response));
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

    public Observable<String> publishedVersion() {
        ReplaySubject<String> subject = ReplaySubject.create();
        Firebase firebase = new Firebase(FirebaseFacadeConstants.getPublishedVersionName());
        firebase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                subject.onNext((String) dataSnapshot.getValue());
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                subject.onError(new FirebaseException(firebaseError.toException()));
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

    private Observable<TokenTradeReport> waitForTokenTradeReport(Firebase ref, long timeoutInSeconds) {
        return readResponse(ref, snapshot -> {
            Object codeValue = snapshot.child(FirebaseFacadeConstants.CODE_PATH).getValue();
            Object messageValue = snapshot.child(FirebaseFacadeConstants.MESSAGE_PATH).getValue();
            if (codeValue != null) {
                return Optional.of(new TokenTradeReport((String) codeValue, Optional.fromNullable((String) messageValue)));
            } else {
                return Optional.absent();
            }
        }, timeoutInSeconds);
    }

    private Observable<List<AttachmentHttpRequest>> waitForAttachmentRequests(Firebase ref, long timeoutInSeconds) {
        return readResponse(ref, snapshot -> {
            if (snapshot.getValue() == null) {
                return Optional.absent();
            }
            List<AttachmentHttpRequest> requests = Lists.newArrayList();
            for (DataSnapshot child : snapshot.getChildren()) {
                Object urlValue = child.child(FirebaseFacadeConstants.URL_PATH).getValue();
                Object accessTokenValue = child.child(FirebaseFacadeConstants.ACCESS_TOKEN_PATH).getValue();
                requests.add(new AttachmentHttpRequest((String) urlValue, (String) accessTokenValue));
            }
            return Optional.of(requests);
        }, timeoutInSeconds);
    }

    private <T> Observable<T> readResponse(Firebase ref, Func1<DataSnapshot, Optional<T>> parser, long timeoutInSeconds) {
        PublishSubject<T> subject = PublishSubject.create();
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Optional<T> value = parser.call(dataSnapshot);
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
