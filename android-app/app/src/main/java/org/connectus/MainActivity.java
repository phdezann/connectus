package org.connectus;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import com.firebase.client.AuthData;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(org.connectus.R.layout.activity_main);

        ListView messages = (ListView) findViewById(R.id.list_view_message);

        Firebase ref = new Firebase("https://connectusnow.firebaseio.com/messages");
        /** TODO: This won't be necessary after setting up authentication **/
        ref.authWithCustomToken("LhonDAsWp0nFkL2wF0lD4gMwebJBSemmoZhM1CLG", new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                System.out.println(authData);
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError) {
                System.out.println(firebaseError);
            }
        });
        MessageAdapter adapter = new MessageAdapter(this, Message.class, R.layout.message_list_item, ref);
        messages.setAdapter(adapter);
    }
}
