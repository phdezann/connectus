package org.connectus;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import com.squareup.picasso.Picasso;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.connectus.dagger.AndroidModule;
import org.connectus.dagger.ConnectusComponent;
import org.connectus.support.NoOpObservable;
import org.connectus.support.RobolectricTestBase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

import javax.inject.Inject;
import javax.inject.Singleton;

public class LoginActivityTest extends RobolectricTestBase {

    @Inject
    GoogleAuthUtilWrapper googleAuthUtilWrapper;
    @Inject
    Repository repository;
    @Inject
    Toaster toaster;
    @Inject
    UserRepository userRepository;
    @Inject
    LoginOrchestrator loginOrchestrator;
    @Inject
    EnvironmentHelper environmentHelper;

    @Before
    public void setup() {
        ConnectusApplication connectusApplication = (ConnectusApplication) RuntimeEnvironment.application;
        ConnectusTestComponent component = DaggerLoginActivityTest_ConnectusTestComponent.builder().mocksModule(new MocksModule()).androidModule(new AndroidModule(connectusApplication)).build();
        connectusApplication.setupComponent(component);
        component.inject(this);
        setupHook();
    }

    @Test
    public void userDismissPickAccount() {
        LoginActivity activity = Robolectric.setupActivity(LoginActivity.class);
        activity.onActivityResult(LoginActivity.RC_GOOGLE_LOGIN, Activity.RESULT_CANCELED, null);

        flush();
        Mockito.verify(toaster).toast(Mockito.anyString());
    }

    @Test
    public void userDenyOAuthPermissions() {
        LoginActivity activity = Robolectric.setupActivity(LoginActivity.class);
        activity.onActivityResult(LoginActivity.OAUTH_PERMISSIONS, Activity.RESULT_CANCELED, null);

        flush();
        Mockito.verify(toaster).toast(Mockito.anyString());
    }

    @Test
    public void userAcceptOAuthPermissions() {
        LoginActivity activity = Robolectric.setupActivity(LoginActivity.class);

        Mockito.when(environmentHelper.isInTest()).thenReturn(true);
        Mockito.when(loginOrchestrator.secondPassSetupOfflineAccess(Mockito.anyString(), Mockito.anyString())).thenReturn(NoOpObservable.justNoOp());
        Mockito.when(userRepository.getUserEmail()).thenReturn(Constants.FAKE_GMAIL_COM);

        Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, Constants.FAKE_GMAIL_COM);
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, Constants.FAKE_AUTHORIZATION_CODE);
        activity.onActivityResult(LoginActivity.OAUTH_PERMISSIONS, Activity.RESULT_OK, intent);

        flush();
        Mockito.verify(toaster).toast(Mockito.anyString());
    }

    @Component(modules = {MocksModule.class, AndroidModule.class})
    @Singleton
    protected interface ConnectusTestComponent extends ConnectusComponent {
        void inject(LoginActivityTest licenseTest);
    }

    @Module
    protected static class MocksModule {

        @Provides
        @Singleton
        public GoogleAuthUtilWrapper provideGoogleAuthUtilWrapper() {
            return Mockito.mock(GoogleAuthUtilWrapper.class);
        }

        @Provides
        @Singleton
        public Repository provideFirebaseFacade() {
            return Mockito.mock(Repository.class);
        }

        @Provides
        @Singleton
        public Toaster provideToaster() {
            return Mockito.mock(Toaster.class);
        }

        @Provides
        @Singleton
        public UserRepository provideUserRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Provides
        @Singleton
        public LoginOrchestrator provideLoginOrchestrator() {
            return Mockito.mock(LoginOrchestrator.class);
        }

        @Provides
        @Singleton
        public EnvironmentHelper provideEnvironmentHelper() {
            return Mockito.mock(EnvironmentHelper.class);
        }

        @Provides
        @Singleton
        public Picasso providePicassoBuilder() {
            return Mockito.mock(Picasso.class);
        }
    }
}
