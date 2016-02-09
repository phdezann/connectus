package org.connectus.support;

import android.os.Build;
import org.assertj.core.api.Assertions;
import org.connectus.BuildConfig;
import org.connectus.RxJavaBlockingHook;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT)
abstract public class RobolectricTestBase extends Assertions {

    static RxJavaBlockingHook hook;

    protected void setupHook() {
        hook = RxJavaBlockingHook.setupHook();
    }

    protected void flush() {
        // flush any work in RxJava
        hook.waitUntilFinished();
        // flush any work left on the main thread
        Robolectric.flushForegroundThreadScheduler();
    }
}
