package org.connectus;

import android.content.SharedPreferences;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.inject.Inject;

public class UserRepository {

    public static final String USER_EMAIL_KEY = "USER_EMAIL";
    public static final String DISPLAY_NAME_KEY = "DISPLAY_NAME";
    public static final String GIVEN_NAME_KEY = "GIVEN_NAME";
    public static final String FAMILY_NAME_KEY = "FAMILY_NAME";
    @Inject
    SharedPreferences sharedPref;

    @AllArgsConstructor
    @Getter
    public static class UserInfo {
        String email;
        String displayName;
        String givenName;
        String familyName;
    }

    @Inject
    public UserRepository() {
    }

    public void persistUserInfo(String email, String displayName, String givenName, String familyName) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(USER_EMAIL_KEY, email);
        editor.putString(DISPLAY_NAME_KEY, displayName);
        editor.putString(GIVEN_NAME_KEY, givenName);
        editor.putString(FAMILY_NAME_KEY, familyName);
        editor.commit();
    }

    public void clearUserInfo() {
        persistUserInfo(null, null, null, null);
    }

    public UserInfo getUserInfo() {
        String userEmail = sharedPref.getString(USER_EMAIL_KEY, null);
        String displayName = sharedPref.getString(DISPLAY_NAME_KEY, null);
        String givenName = sharedPref.getString(GIVEN_NAME_KEY, null);
        String familyName = sharedPref.getString(FAMILY_NAME_KEY, null);
        return new UserInfo(userEmail, displayName, givenName, familyName);
    }

    public boolean isUserLoggedIn() {
        return sharedPref.getString(USER_EMAIL_KEY, null) != null;
    }

    public String getUserEmail() {
        return getUserInfo().getEmail();
    }
}
