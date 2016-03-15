package org.connectus;

import android.app.Application;
import com.firebase.client.Firebase;
import lombok.Getter;
import org.connectus.dagger.AndroidModule;
import org.connectus.dagger.AppModule;
import org.connectus.dagger.ConnectusComponent;
import org.connectus.dagger.DaggerConnectusMainComponent;

public class ConnectusApplication extends Application {

    @Getter
    ConnectusComponent component;

    @Override
    public void onCreate() {
        super.onCreate();
        Firebase.setAndroidContext(this);
        onPostCreate();
    }

    protected void onPostCreate() {
        setupComponent(DaggerConnectusMainComponent.builder().androidModule(new AndroidModule(this)).appModule(new AppModule()).build());
    }

    public void setupComponent(ConnectusComponent admvComponent) {
        component = admvComponent;
        component.inject(this);
    }
}
