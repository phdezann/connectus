package org.connectus;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.connectus.model.GmailThread;
import org.connectus.model.Resident;
import org.connectus.support.NoOpObservable.NoOp;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import javax.inject.Inject;

@Slf4j
public class MainActivity extends Activity {

    static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 1;
    static final int RC_GOOGLE_LOGIN = 2;
    static final int OAUTH_PERMISSIONS = 3;

    GoogleApiClient mGoogleApiClient;
    CompositeSubscription subs = new CompositeSubscription();

    @Inject
    Toaster toaster;
    @Inject
    LoginOrchestrator loginOrchestrator;
    @Inject
    EnvironmentHelper environmentHelper;
    @Inject
    UserRepository userRepository;
    @Inject
    FirebaseFacade firebaseFacade;

    Firebase ref;
    Firebase.AuthStateListener mAuthListener;
    TextView connectedUser;
    ListView messagesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.activity_login);

        findViewById(R.id.login_with_google).setOnClickListener(view -> onSignInGooglePressed());
        connectedUser = (TextView) findViewById(R.id.connected_user);

        findViewById(R.id.logout).setOnClickListener(view -> logout());
        messagesListView = (ListView) findViewById(R.id.list_view_message);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN) //
                .requestEmail() //
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this) //
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso) //
                .build();

        if (environmentHelper.isNotInTest()) {
            mGoogleApiClient.connect();
            ref = new Firebase(FirebaseFacadeConstants.getRootUrl());
            mAuthListener = authData -> {
                if (authData == null) {
                    logout();
                }
            };
            ref.addAuthStateListener(mAuthListener);
            setupMessageAdapter();

            if (userRepository.isUserLoggedIn()) {
                onLoginSuccess();
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

    public void addResidentAndAddContact(String residentName) {
        firebaseFacade.addResident(userRepository.getUserEmail(), residentName, Resident.deriveLabelName(residentName));
    }

    public void onAddContact(String emailOfContact, String residentId, Optional<String> previousBoundResidentId) {
        firebaseFacade.updateContact(userRepository.getUserEmail(), residentId, emailOfContact, previousBoundResidentId);
    }

    public void onSignInGooglePressed() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            chooseGoogleAccount();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST_GET_ACCOUNTS);
        }
    }

    private void chooseGoogleAccount() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
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

    private void subscribe(Observable<NoOp> obs) {
        subs.add(obs.subscribeOn(Schedulers.io()) //
                .observeOn(AndroidSchedulers.mainThread()) //
                .subscribe(authData -> onLoginSuccess(), e -> {
                    e.printStackTrace();
                    toaster.toast("Error: " + Throwables.getStackTraceAsString(e));
                }));
    }

    private Observable<NoOp> askOAuthPermissionsOnUserRecoverableAuthException(Throwable e) {
        return e instanceof UserRecoverableAuthException ? askOAuthPermissions((UserRecoverableAuthException) e) : Observable.error(e);
    }

    private Observable<NoOp> askOAuthPermissions(UserRecoverableAuthException urae) {
        return Observable.create(s -> {
            Intent recover = urae.getIntent();
            startActivityForResult(recover, OAUTH_PERMISSIONS);
            s.onCompleted();
        });
    }

    private void onLoginSuccess() {
        messagesListView.setVisibility(View.VISIBLE);
        toaster.toast(getString(R.string.on_logging_success));
        connectedUser.setText(userRepository.getUserEmail());
        setupMessageAdapter();
    }

    private void setupMessageAdapter() {
        if (userRepository.isUserLoggedIn()) {
            Firebase ref = new Firebase(FirebaseFacadeConstants.getAdminMessagesUrl(FirebaseFacade.encode(userRepository.getUserEmail())));
            ThreadAdapter adapter = new ThreadAdapter(this, GmailThread.class, R.layout.thread_list_item, ref);
            messagesListView.setAdapter(adapter);

            messagesListView.setOnItemClickListener((parent, view, position, id) -> {
                GmailThread thread = adapter.getItem(position);
                ResidentListDialogFragment fragment = new ResidentListDialogFragment();
                Bundle args = new Bundle();
                args.putString(ResidentListDialogFragment.CONTACT_EMAIL_ARG, thread.getContactEmailOpt().get());
                args.putString(ResidentListDialogFragment.BOUND_RESIDENT_ID_ARG, thread.getLastMessage().getResidentOpt().transform(r -> r.getId()).orNull());
                fragment.setArguments(args);

                fragment.show(getFragmentManager(), ResidentListDialogFragment.class.getSimpleName());
            });

            messagesListView.setOnItemLongClickListener((parent, view, position, id) -> {
                GmailThread thread = adapter.getItem(position);
                Optional<Resident> residentOpt = thread.getLastMessage().getResidentOpt();
                if (residentOpt.isPresent()) {
                    Resident resident = residentOpt.get();
                    Intent intent = new Intent(this, ResidentThreadListActivity.class);
                    intent.putExtra(ResidentThreadListActivity.RESIDENT_ID_ARG, resident.getId());
                    startActivity(intent);
                } else {
                    toaster.toast(getString(R.string.no_resident_associated));
                }
                return true;
            });
        }
    }

    private void onLoginError() {
        toaster.toast(getString(R.string.on_login_error));
    }

    private void logout() {
        ref.unauth();
        if (mGoogleApiClient.isConnected()) {
            Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(status -> {});
        }
        userRepository.clearUserInfo();
        onLogoutSuccess();
    }

    private void onLogoutSuccess() {
        connectedUser.setText("");
        messagesListView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        if (environmentHelper.isNotInTest()) {
            ref.removeAuthStateListener(mAuthListener);
            mGoogleApiClient.disconnect();
        }
        subs.unsubscribe();
        super.onDestroy();
    }
}
