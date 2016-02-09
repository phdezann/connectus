package org.connectus;

import android.content.Context;
import android.widget.Toast;

import javax.inject.Inject;

public class Toaster {

    Context context;

    @Inject
    public Toaster(Context context) {
        this.context = context;
    }

    public void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }
}
