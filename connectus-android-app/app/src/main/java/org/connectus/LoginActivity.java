package org.connectus;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import com.firebase.client.Firebase;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.connectus.support.NoOpObservable;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import javax.inject.Inject;

@Slf4j
public class LoginActivity extends ActivityBase {

    static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 1;
    static final int RC_GOOGLE_LOGIN = 2;
    static final int OAUTH_PERMISSIONS = 3;

    @Inject
    Toaster toaster;
    @Inject
    LoginOrchestrator loginOrchestrator;

    Firebase.AuthStateListener loginAuthListener;
    ProgressDialog loginProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.login);
        findViewById(R.id.login_with_google).setOnClickListener(view -> onSignInGooglePressed());

        loginProgressDialog = new ProgressDialog(this);
        loginProgressDialog.setMessage(getString(R.string.login_progress_dialog_message));
        loginProgressDialog.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startListeningForLogin();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopListeningForLogin();
    }

    public void onSignInGooglePressed() {
        loginProgressDialog.show();
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            chooseGoogleAccount();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST_GET_ACCOUNTS);
        }
    }

    private void chooseGoogleAccount() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
        startActivityForResult(signInIntent, RC_GOOGLE_LOGIN);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_GET_ACCOUNTS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseGoogleAccount();
                } else {
                    toaster.toast("Contact permission is required");
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_LOGIN) { // callback after interacting with the google plus accounts popup
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (resultCode == RESULT_CANCELED) { // the user dismissed the popup for choosing the google plus account
                onLoginError();
            } else if (result.isSuccess()) {
                String googleAccountEmail = result.getSignInAccount().getEmail();
                subscribe(loginOrchestrator.loginAndCheckRefreshToken(googleAccountEmail) //
                        .onErrorResumeNext(e -> askOAuthPermissionsOnUserRecoverableAuthException(e)));
            }
        } else if (requestCode == OAUTH_PERMISSIONS) { // callback after interacting with the oauth permissions screen
            if (resultCode == RESULT_CANCELED) { // permissions have been denied by the user
                onLoginError();
            } else if (resultCode == RESULT_OK) {
                Bundle bundle = data.getExtras();
                String googleAccountEmail = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                String authorizationToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                subscribe(loginOrchestrator.secondPassSetupOfflineAccess(googleAccountEmail, authorizationToken));
            }
        }
    }

    private void subscribe(Observable<NoOpObservable.NoOp> obs) {
        subs.add(obs.subscribeOn(Schedulers.io()) //
                .observeOn(AndroidSchedulers.mainThread()) //
                .subscribe(authData -> onLoginSuccess(), e -> {
                    e.printStackTrace();
                    toaster.toast("Error: " + Throwables.getStackTraceAsString(e));
                }));
    }

    private Observable<NoOpObservable.NoOp> askOAuthPermissionsOnUserRecoverableAuthException(Throwable e) {
        return e instanceof UserRecoverableAuthException ? askOAuthPermissions((UserRecoverableAuthException) e) : Observable.error(e);
    }

    private Observable<NoOpObservable.NoOp> askOAuthPermissions(UserRecoverableAuthException urae) {
        return Observable.create(s -> {
            Intent recover = urae.getIntent();
            startActivityForResult(recover, OAUTH_PERMISSIONS);
            s.onCompleted();
        });
    }

    private void onLoginSuccess() {
        loginProgressDialog.dismiss();
        toaster.toast(getString(R.string.on_logging_success));
        startMainActivity();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void onLoginError() {
        loginProgressDialog.dismiss();
        toaster.toast(getString(R.string.on_login_error));
    }

    private void startListeningForLogin() {
        loginAuthListener = authData -> {
            if (authData != null) {
                onLoginSuccess();
            }
            stopListeningForLogin();
        };

        if (environmentHelper.isNotInTest()) {
            firebaseRef.addAuthStateListener(loginAuthListener);
        }
    }

    private void stopListeningForLogin() {
        if (environmentHelper.isNotInTest()) {
            firebaseRef.removeAuthStateListener(loginAuthListener);
        }
    }
}
