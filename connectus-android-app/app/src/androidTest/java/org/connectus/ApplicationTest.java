package org.connectus;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import com.google.common.base.Throwables;
import com.squareup.picasso.Picasso;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.io.IOUtils;
import org.connectus.dagger.AndroidModule;
import org.connectus.dagger.ConnectusComponent;
import org.connectus.support.RxJavaIdlingResource;
import org.joda.time.LocalDateTime;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class ApplicationTest extends ActivityInstrumentationTestCase2<LoginActivity> {

    RxJavaIdlingResource rxJavaIdlingResource;

    @Inject
    EnvironmentHelper environmentHelper;

    LoginActivity activity;

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

        activity = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        Espresso.unregisterIdlingResources(rxJavaIdlingResource);
        super.tearDown();
    }

    public ApplicationTest() {
        super(LoginActivity.class);
    }

    public void testUi() {
        Mockito.when(environmentHelper.isInTest()).thenReturn(true);
        onView(withId(R.id.login_with_google)).check(matches(isDisplayed()));
    }

    protected static void takeScreenshot(Activity activity) {
        File picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String date = LocalDateTime.now().toString("yyyy-MM-dd-HH-mm-ss");
        File output = new File(picturesDirectory, date + ".png");

        View scrView = activity.getWindow().getDecorView().getRootView();
        scrView.setDrawingCacheEnabled(true);

        Bitmap bitmap = Bitmap.createBitmap(scrView.getDrawingCache());
        scrView.setDrawingCacheEnabled(false);

        OutputStream out = null;
        try {
            out = new FileOutputStream(output);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
        } catch (FileNotFoundException e) {
            Throwables.propagate(e);
        } catch (IOException e) {
            Throwables.propagate(e);
        } finally {
            IOUtils.closeQuietly(out);
        }
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

        @Provides
        @Singleton
        public Picasso providePicassoBuilder() {
            return Mockito.mock(Picasso.class);
        }

    }
}
