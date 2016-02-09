package org.connectus;

import com.firebase.client.AuthData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;
import rx.functions.Func1;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static org.connectus.support.NoOpObservable.justNoOp;

public class LoginOrchestrator {

    @Inject
    GoogleAuthUtilWrapper googleAuthUtilWrapper;
    @Inject
    AccountManagerUtil accountManagerUtil;
    @Inject
    FirebaseFacade firebaseFacade;
    @Inject
    UserRepository userRepository;

    @Inject
    public LoginOrchestrator() {
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class LoginCredentials {
        String androidId;
        String authorizationCode;
    }

    public Observable<NoOp> loginAndCheckRefreshToken(String email) {
        /**
         * the authWithOAuthToken's callback does not get invoked at all if two calls in a row happen very quickly
         * in this case the observable never completes and the flatMap chain is stuck. The timeout used below is a
         * quick solution for this.
         */
        return firebaseLogin(email).timeout(FirebaseFacadeConstants.LOGIN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS, firebaseLogin(email)) //
                .flatMap(authData -> persistUserInfo(authData)) //
                .flatMap(authData -> firebaseFacade.isRefreshTokenAvailable(email)) //
                .flatMap(refreshTokenAvailable -> refreshTokenAvailable ? justNoOp() : setupOfflineAccess(email));
    }

    protected Observable<AuthData> firebaseLogin(String email) {
        return accountManagerUtil.findAccount(email) //
                .flatMap(account -> googleAuthUtilWrapper.getAccessToken(account)) //
                .flatMap(accessToken -> firebaseFacade.loginWithGoogle(accessToken));
    }

    protected Observable<NoOp> setupOfflineAccess(String email) {
        return accountManagerUtil.findAccount(email) //
                .flatMap(account -> Observable.zip( //
                        googleAuthUtilWrapper.getAndroidId(account), //
                        googleAuthUtilWrapper.getAuthorizationCode(account), //
                        (androidId, token) -> new LoginCredentials(androidId, token))) //
                .flatMap(creds -> firebaseFacade.sendCredentials(creds)) //
                .retryWhen(expiredAuthorizationCode());
    }

    protected Observable<NoOp> setupOfflineAccess(String email, String authToken) {
        return accountManagerUtil.findAccount(email) //
                .flatMap(account -> googleAuthUtilWrapper.getAndroidId(account)) //
                .map(androidId -> new LoginCredentials(androidId, authToken)) //
                .flatMap(creds -> firebaseFacade.sendCredentials(creds)) //
                .retryWhen(expiredAuthorizationCode());
    }

    // this gives one chance to obtain a new authorization code if an ExpiredAuthorizationCodeException has been thrown
    private Func1<Observable<? extends Throwable>, Observable<?>> expiredAuthorizationCode() {
        return attempts -> attempts.zipWith(Observable.range(1, 2), (n, i) -> n) //
                .flatMap(n -> {
                    if (n instanceof FirebaseFacade.ExpiredAuthorizationCodeException) {
                        String authorizationCode = ((FirebaseFacade.ExpiredAuthorizationCodeException) n).getAuthorizationCode();
                        return googleAuthUtilWrapper.clearToken(authorizationCode);
                    } else {
                        return Observable.error(n);
                    }
                });
    }

    private Observable<AuthData> persistUserInfo(AuthData authData) {
        return Observable.create(s -> {
            userRepository.persistUserInfo( //
                    getString(authData, "email"), //
                    getString(authData, "displayName"), //
                    getString(authData, "givenName"), //
                    getString(authData, "familyName"));
            s.onNext(authData);
            s.onCompleted();
        });
    }

    private String getString(AuthData authData, String key) {
        return (String) authData.getProviderData().get(key);
    }
}
