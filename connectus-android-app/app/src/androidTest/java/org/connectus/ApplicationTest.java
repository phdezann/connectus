package org.connectus;

import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.test.ActivityInstrumentationTestCase2;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.connectus.dagger.AndroidModule;
import org.connectus.dagger.ConnectusComponent;
import org.connectus.support.RxJavaIdlingResource;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.inject.Singleton;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class ApplicationTest extends ActivityInstrumentationTestCase2<MainActivity> {

    RxJavaIdlingResource rxJavaIdlingResource;

    @Inject
    EnvironmentHelper environmentHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        RxJavaBlockingHook hook = RxJavaBlockingHook.setupHook();
        rxJavaIdlingResource = new RxJavaIdlingResource(hook);
        Espresso.registerIdlingResources(rxJavaIdlingResource);

        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        ConnectusApplication connectusApplication = (ConnectusApplication) getInstrumentation().getTargetContext().getApplicationContext();

        ConnectusTestComponent component = DaggerApplicationTest_ConnectusTestComponent.builder().mocksModule(new MocksModule()).androidModule(new AndroidModule(connectusApplication)).build();
        connectusApplication.setupComponent(component);
        component.inject(this);

        getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        Espresso.unregisterIdlingResources(rxJavaIdlingResource);
        super.tearDown();
    }

    public ApplicationTest() {
        super(MainActivity.class);
    }

    public void testUi() {
        Mockito.when(environmentHelper.isInTest()).thenReturn(true);
        onView(withId(R.id.login_with_google)).check(matches(isDisplayed()));
    }

    @Component(modules = {MocksModule.class, AndroidModule.class})
    @Singleton
    protected interface ConnectusTestComponent extends ConnectusComponent {
        void inject(ApplicationTest applicationTest);
    }

    @Module
    protected static class MocksModule {

        @Provides
        @Singleton
        public EnvironmentHelper provideEnvironmentHelper() {
            return Mockito.mock(EnvironmentHelper.class);
        }
    }
}
