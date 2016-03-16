package org.connectus;

import android.accounts.Account;
import android.content.Intent;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.common.base.Optional;
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
import org.robolectric.RuntimeEnvironment;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

public class LoginOrchestratorTest extends RobolectricTestBase {

    @Inject
    GoogleAuthUtilWrapper googleAuthUtilWrapper;
    @Inject
    FirebaseFacade firebaseFacade;
    @Inject
    LoginOrchestrator loginOrchestrator;
    @Inject
    AccountManagerUtil accountManagerUtil;

    @Before
    public void setup() {
        ConnectusApplication connectusApplication = (ConnectusApplication) RuntimeEnvironment.application;
        ConnectusTestComponent component = DaggerLoginOrchestratorTest_ConnectusTestComponent.builder().mocksModule(new MocksModule()).androidModule(new AndroidModule(connectusApplication)).build();
        connectusApplication.setupComponent(component);
        component.inject(this);
        setupHook();
    }

    @Test
    public void askPermission() throws Exception {
        Mockito.when(accountManagerUtil.findAccount(Mockito.any())).thenReturn(Observable.just(new Account(Constants.FAKE_GMAIL_COM, AccountManagerUtil.GOOGLE_ACCOUNT_TYPE)));
        Mockito.when(googleAuthUtilWrapper.getAndroidId(Mockito.any())).thenReturn(Observable.just(Constants.FAKE_ANDROID_ID));
        Mockito.when(googleAuthUtilWrapper.getAuthorizationCode(Mockito.any())).thenReturn(Observable.error(new UserRecoverableAuthException("", new Intent())));

        try {
            loginOrchestrator.firstPassSetupOfflineAccess(Constants.FAKE_GMAIL_COM).toBlocking().single();
            fail("Should have failed");
        } catch (Exception e) {
            assertThat(e).hasRootCauseInstanceOf(UserRecoverableAuthException.class);
        }
    }

    @Test
    public void retrieveAuthorizationCode() throws Exception {
        Mockito.when(accountManagerUtil.findAccount(Mockito.any())).thenReturn(Observable.just(new Account(Constants.FAKE_GMAIL_COM, AccountManagerUtil.GOOGLE_ACCOUNT_TYPE)));
        Mockito.when(googleAuthUtilWrapper.getAndroidId(Mockito.any())).thenReturn(Observable.just(Constants.FAKE_ANDROID_ID));
        Mockito.when(googleAuthUtilWrapper.getAuthorizationCode(Mockito.any())).thenReturn(Observable.just(Constants.FAKE_AUTHORIZATION_CODE));
        Mockito.when(firebaseFacade.sendCredentials(Mockito.any())).thenReturn(NoOpObservable.justNoOp());

        NoOpObservable.NoOp single = loginOrchestrator.firstPassSetupOfflineAccess(Constants.FAKE_GMAIL_COM).toBlocking().single();
        assertThat(single).isEqualTo(NoOpObservable.noOp());
    }

    @Test
    public void rejectedAuthorizationCode() throws Exception {
        Mockito.when(accountManagerUtil.findAccount(Mockito.any())).thenReturn(Observable.just(new Account(Constants.FAKE_GMAIL_COM, AccountManagerUtil.GOOGLE_ACCOUNT_TYPE)));
        Mockito.when(googleAuthUtilWrapper.getAndroidId(Mockito.any())).thenReturn(Observable.just(Constants.FAKE_ANDROID_ID));
        Mockito.when(googleAuthUtilWrapper.getAuthorizationCode(Mockito.any())).thenReturn(Observable.just(Constants.FAKE_AUTHORIZATION_CODE));
        Mockito.when(googleAuthUtilWrapper.clearToken(Mockito.any())).thenReturn(NoOpObservable.justNoOp());
        Mockito.when(firebaseFacade.sendCredentials(Mockito.any())) //
                .thenReturn(Observable.error(new FirebaseFacade.ExpiredAuthorizationCodeException(new FirebaseFacade.TokenTradeReport("FAKE_ERROR_CODE", Optional.absent()), Constants.FAKE_AUTHORIZATION_CODE))) //
                .thenReturn(NoOpObservable.justNoOp());

        NoOpObservable.NoOp single = loginOrchestrator.firstPassSetupOfflineAccess(Constants.FAKE_GMAIL_COM).toBlocking().single();

        Mockito.verify(firebaseFacade, Mockito.times(2)).sendCredentials(Mockito.any());
        Mockito.verify(googleAuthUtilWrapper).clearToken(Mockito.any());
        assertThat(single).isEqualTo(NoOpObservable.noOp());
    }

    @Component(modules = {MocksModule.class, AndroidModule.class})
    @Singleton
    public interface ConnectusTestComponent extends ConnectusComponent {
        void inject(LoginOrchestratorTest loginOrchestratorTest);
    }

    @Module
    protected class MocksModule {

        @Provides
        @Singleton
        public GoogleAuthUtilWrapper provideGoogleAuthUtilWrapper() {
            return Mockito.mock(GoogleAuthUtilWrapper.class);
        }

        @Provides
        @Singleton
        public FirebaseFacade provideFirebaseFacade() {
            return Mockito.mock(FirebaseFacade.class);
        }

        @Provides
        @Singleton
        public Toaster provideToaster() {
            return Mockito.mock(Toaster.class);
        }

        @Provides
        @Singleton
        public AccountManagerUtil provideAccountManagerUtil() {
            return Mockito.mock(AccountManagerUtil.class);
        }
    }
}
