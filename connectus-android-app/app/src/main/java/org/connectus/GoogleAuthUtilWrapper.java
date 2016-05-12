package org.connectus;

import android.accounts.Account;
import android.content.Context;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;

import javax.inject.Inject;
import java.io.IOException;

import static org.connectus.support.NoOpObservable.noOp;

public class GoogleAuthUtilWrapper {

    private Context context;
    private String authorizationCodeScope;
    private String androidIdScope;
    private String accessTokenScope;

    @Inject
    public GoogleAuthUtilWrapper(Context context) {
        this.context = context;
        String clientId = context.getString(R.string.google_developers_console_web_client_id);
        String gmailScopes = context.getString(R.string.gmail_scopes);
        this.authorizationCodeScope = context.getString(R.string.authorization_code_scope, clientId, gmailScopes);
        this.androidIdScope = context.getString(R.string.android_id_scope, clientId);
        this.accessTokenScope = context.getString(R.string.access_token_scopes);
    }

    public Observable<String> getAuthorizationCode(Account account) {
        return Observable.create(s -> {
            try {
                String token = GoogleAuthUtil.getToken(context, account, authorizationCodeScope);
                s.onNext(token);
                s.onCompleted();
            } catch (IOException e) {
                s.onError(e);
            } catch (GoogleAuthException e) {
                s.onError(e);
            }
        });
    }

    public Observable<String> getAndroidId(Account account) {
        return Observable.create(s -> {
            try {
                String token = GoogleAuthUtil.getToken(context, account, androidIdScope);
                s.onNext(token);
                s.onCompleted();
            } catch (IOException e) {
                s.onError(e);
            } catch (GoogleAuthException e) {
                s.onError(e);
            }
        });
    }

    public Observable<Repository.AccessToken> getAccessToken(Account account) {
        return Observable.create(s -> {
            try {
                String token = GoogleAuthUtil.getToken(context, account, accessTokenScope);
                s.onNext(new Repository.AccessToken(token));
                s.onCompleted();
            } catch (IOException e) {
                s.onError(e);
            } catch (GoogleAuthException e) {
                s.onError(e);
            }
        });
    }

    public Observable<NoOp> clearToken(String invalidToken) {
        return Observable.create(s -> {
            try {
                GoogleAuthUtil.clearToken(context, invalidToken);
                s.onNext(noOp());
                s.onCompleted();
            } catch (IOException e) {
                s.onError(e);
            } catch (GoogleAuthException e) {
                s.onError(e);
            }
        });
    }
}
