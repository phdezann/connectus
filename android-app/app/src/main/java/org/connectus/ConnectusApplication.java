package org.connectus;

import android.app.Application;
import com.firebase.client.Firebase;

public class ConnectusApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Firebase.setAndroidContext(this);
    }
}
