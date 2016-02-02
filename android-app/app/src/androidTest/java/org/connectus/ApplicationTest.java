package org.connectus;

import android.test.ActivityInstrumentationTestCase2;

public class ApplicationTest extends ActivityInstrumentationTestCase2<MainActivity> {

    public ApplicationTest() {
        super(MainActivity.class);
    }

    public void testUi() {
        getActivity();
    }
}
