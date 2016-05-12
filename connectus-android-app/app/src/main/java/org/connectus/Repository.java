package org.connectus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.AttachmentFirebaseHttpRequest;
import org.connectus.model.OutboxMessage;
import org.connectus.model.Resident;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.connectus.FirebaseFacadeConstants.*;
import static org.connectus.support.NoOpObservable.noOp;

public class Repository {

    @Inject
    FirebaseObservableWrappers wrappers;

    @Inject
    public Repository() {
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

    public void addResident(String email, String name, String labelName) {
        Firebase ref = new Firebase(getResidentsUrl(encode(email)));
        Resident resident = new Resident();
        resident.setName(name);
        resident.setLabelName(labelName);
        Map<String, Object> values = new ObjectMapper().convertValue(resident, Map.class);
        ref.push().updateChildren(values);
    }

    public void updateContact(String email, String residentId, String emailOfContact, Optional<String> previousResidentIdOpt) {
        Firebase ref = new Firebase(getContactsUrl(encode(email)));
        Map<String, Object> values = Maps.newHashMap();
        if (previousResidentIdOpt.isPresent()) {
            String previousResidentId = previousResidentIdOpt.get();
            if (!previousResidentId.equals(residentId)) {
                values.put(residentId + "/" + Repository.encode(emailOfContact), DEFAULT_VALUE);
                values.put(previousResidentId + "/" + Repository.encode(emailOfContact), null);
            } else {
                values.put(residentId + "/" + Repository.encode(emailOfContact), null);
            }
        } else {
            values.put(residentId + "/" + Repository.encode(emailOfContact), DEFAULT_VALUE);
        }
        ref.updateChildren(values);
    }

    public Observable<NoOp> sendCredentials(LoginOrchestrator.LoginCredentials creds) {
        Firebase authorizationCodesUrl = new Firebase(getAuthorizationCodesUrl());

        Map<String, Object> values = Maps.newHashMap();
        values.put(ANDROID_ID_PATH, creds.getAndroidId());
        values.put(AUTHORIZATION_CODE_PATH, creds.getAuthorizationCode());

        Firebase push = authorizationCodesUrl.push();
        String authorizationId = push.getKey();

        Firebase authorizationIdRef = new Firebase(getAuthorizationIdUrl(authorizationId));
        Firebase reportRef = new Firebase(getReportUrl(authorizationId));

        return wrappers.updateChildren(push, values) // push AndroidId and AuthorizationCode to the server
                .flatMap(ignore -> waitForTokenTradeReport(reportRef, SERVER_PROCESSING_TIMEOUT_IN_SECONDS)) //
                .flatMap(report -> wrappers.clear(authorizationIdRef).map(noOp -> report)) //
                .flatMap(report -> checkErrors(creds, report)) //
                .map(ignore -> noOp());
    }

    public Observable<NoOp> addOutboxMessage(String email, String residentId, String to, String threadId, String personal, String subject, String content) {
        Firebase ref = new Firebase(getOutboxUrl(email));
        OutboxMessage outboxMsg = new OutboxMessage(residentId, to, threadId, personal, subject, content);
        Map<String, Object> values = new ObjectMapper().convertValue(outboxMsg, Map.class);
        Firebase newOutboxMessage = ref.push();
        return wrappers.updateChildren(newOutboxMessage, values);
    }

    public Observable<List<AttachmentFirebaseHttpRequest>> getAttachmentRequests(String email, String messageId) {
        Firebase ref = new Firebase(getAttachmentRequestUrl(email));

        Map<String, Object> values = Maps.newHashMap();
        values.put(messageId, DEFAULT_VALUE);

        Firebase requestRef = ref.child(ATTACHMENT_REQUESTS);
        Firebase responseRef = ref.child(ATTACHMENT_RESPONSES);

        return wrappers.updateChildren(requestRef, values) //
                .flatMap(ignore -> waitForAttachmentRequests(responseRef, SERVER_PROCESSING_TIMEOUT_IN_SECONDS)) //
                .flatMap(response -> wrappers.clear(requestRef).flatMap(noOp -> wrappers.clear(responseRef)) //
                        .map(noOp -> response));
    }

    private Observable<Repository.TokenTradeReport> checkErrors(LoginOrchestrator.LoginCredentials creds, Repository.TokenTradeReport tokenTradeReport) {
        if (StringUtils.equals(tokenTradeReport.code, LOGIN_CODE_SUCCESS)) {
            return Observable.just(tokenTradeReport);
        } else if (StringUtils.equals(tokenTradeReport.code, LOGIN_CODE_INVALID_GRANT)) {
            return Observable.error(new Repository.ExpiredAuthorizationCodeException(tokenTradeReport, creds.getAuthorizationCode()));
        } else {
            return Observable.error(new Repository.BulkException(tokenTradeReport));
        }
    }

    public Observable<Boolean> isRefreshTokenAvailable(String email) {
        Firebase refreshTokenRef = new Firebase(getRefreshTokenUrl(encode(email)));
        return wrappers.read(refreshTokenRef).map(value -> value != null);
    }

    public Observable<String> publishedVersion() {
        Firebase firebase = new Firebase(getPublishedVersionName());
        return wrappers.listen(firebase).map(dataSnapshot -> (String) dataSnapshot.getValue());
    }

    public Observable<String> backendStatus() {
        Firebase firebase = new Firebase(getBackendStatus());
        return wrappers.listen(firebase).map(dataSnapshot -> (String) dataSnapshot.getValue());
    }

    public Observable<AuthData> loginWithGoogle(AccessToken token) {
        Firebase firebase = new Firebase(getRootUrl());
        return wrappers.authWithOAuthToken(firebase, OAUTH_GOOGLE_PROVIDER, token.value);
    }

    private Observable<TokenTradeReport> waitForTokenTradeReport(Firebase ref, long timeoutInSeconds) {
        return wrappers.listen(ref, snapshot -> {
            Object codeValue = snapshot.child(CODE_PATH).getValue();
            Object messageValue = snapshot.child(MESSAGE_PATH).getValue();
            if (codeValue != null) {
                return Optional.of(new TokenTradeReport((String) codeValue, Optional.fromNullable((String) messageValue)));
            } else {
                return Optional.absent();
            }
        }, timeoutInSeconds);
    }

    private Observable<List<AttachmentFirebaseHttpRequest>> waitForAttachmentRequests(Firebase ref, long timeoutInSeconds) {
        return wrappers.listen(ref, snapshot -> {
            if (snapshot.getValue() == null) {
                return Optional.absent();
            }
            List<AttachmentFirebaseHttpRequest> requests = Lists.newArrayList();
            for (DataSnapshot child : snapshot.getChildren()) {
                Object urlValue = child.child(URL_PATH).getValue();
                Object accessTokenValue = child.child(ACCESS_TOKEN_PATH).getValue();
                Object mimeType = child.child(MIME_TYPE_PATH).getValue();
                requests.add(new AttachmentFirebaseHttpRequest((String) urlValue, (String) accessTokenValue, (String) mimeType));
            }
            return Optional.of(requests);
        }, timeoutInSeconds);
    }

    public static String encode(String email) {
        return email.replace('.', ',');
    }
}
