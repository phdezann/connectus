package org.connectus;

import javax.inject.Inject;

public class EnvironmentHelper {

    @Inject
    public EnvironmentHelper() {
    }

    public boolean isInTest() {
        return false;
    }

    public boolean isNotInTest() {
        return !isInTest();
    }
}
