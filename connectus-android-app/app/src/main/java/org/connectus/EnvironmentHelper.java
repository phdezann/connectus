package org.connectus;

import javax.inject.Inject;

public class EnvironmentHelper {

    @Inject
    public EnvironmentHelper() {
    }

    public boolean isReleaseBuildType() {
        return BuildConfig.BUILD_TYPE.equals("release");
    }

    public boolean isInTest() {
        return false;
    }

    public boolean isNotInTest() {
        return !isInTest();
    }
}
