package org.connectus;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import com.firebase.client.Firebase;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.GoogleApiClient;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import javax.inject.Inject;

@Slf4j
abstract public class ActivityBase extends AppCompatActivity {

    @Inject
    EnvironmentHelper environmentHelper;
    @Inject
    UserRepository userRepository;
    @Inject
    FirebaseFacade firebaseFacade;
    @Inject
    Toaster toaster;

    CompositeSubscription subs = new CompositeSubscription();
    GoogleApiClient googleApiClient;
    Firebase firebaseRef;
    Firebase.AuthStateListener logoutAuthListener;
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN) //
                .requestEmail() //
                .build();

        googleApiClient = new GoogleApiClient.Builder(this) //
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso) //
                .build();

        if (environmentHelper.isNotInTest()) {
            googleApiClient.connect();
            firebaseRef = new Firebase(FirebaseFacadeConstants.getRootUrl());
            logoutAuthListener = authData -> {
                if (authData == null || !userRepository.isUserLoggedIn()) {
                    logout();
                }
            };
            if (allActivitiesExceptLogin()) {
                firebaseRef.addAuthStateListener(logoutAuthListener);
            }
        }

        if (environmentHelper.isReleaseBuildType()) {
            Observable<String> publishedVersionObs = firebaseFacade.publishedVersion();
            subs.add(publishedVersionObs.subscribeOn(Schedulers.io()) //
                    .observeOn(AndroidSchedulers.mainThread()) //
                    .subscribe(publishedVersion -> {
                        if (publishedVersion != null && !publishedVersion.equals(BuildConfig.VERSION_NAME)) {
                            showUpdateAppModalDialog();
                        }
                    }));
        }
    }

    private void showUpdateAppModalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.update_available_title);
        builder.setMessage(R.string.update_available);
        builder.setPositiveButton(R.string.update, (dialog, which) -> {
            // use a setOnClickListener instead in order not to close the dialog on button click
        });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            } catch (android.content.ActivityNotFoundException ignore) {
                toaster.toast(getString(R.string.update_available_no_google_play));
            }
        });
    }

    private boolean allActivitiesExceptLogin() {
        return !(this instanceof LoginActivity);
    }

    protected void logout() {
        firebaseRef.unauth();
        signOutGoogleApiClient();
        userRepository.clearUserInfo();
        startLoginActivity();
    }

    private void signOutGoogleApiClient() {
        if (googleApiClient.isConnected()) {
            Auth.GoogleSignInApi.signOut(googleApiClient).setResultCallback(status -> {});
        }
    }

    private void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onDestroy() {
        if (environmentHelper.isNotInTest() && allActivitiesExceptLogin()) {
            firebaseRef.removeAuthStateListener(logoutAuthListener);
            googleApiClient.disconnect();
        }
        subs.unsubscribe();
        super.onDestroy();
    }
}
