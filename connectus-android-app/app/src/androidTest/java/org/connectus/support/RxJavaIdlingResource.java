package org.connectus.support;

import android.support.test.espresso.IdlingResource;
import android.util.Log;
import org.connectus.RxJavaBlockingHook;

public class RxJavaIdlingResource implements IdlingResource {
    public static final String TAG = RxJavaIdlingResource.class.getSimpleName();
    private RxJavaBlockingHook hook;

    public RxJavaIdlingResource(RxJavaBlockingHook hook) {
        this.hook = hook;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public boolean isIdleNow() {
        int activeSubscriptionCount = hook.getCount();
        boolean isIdle = activeSubscriptionCount == 0;
        Log.d(TAG, "activeSubscriptionCount: " + activeSubscriptionCount);
        Log.d(TAG, "isIdleNow: " + isIdle);
        return isIdle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback resourceCallback) {
        Log.d(TAG, "registerIdleTransitionCallback");
        hook.addSubscriptionCompletedListener(() -> resourceCallback.onTransitionToIdle());
    }
}
