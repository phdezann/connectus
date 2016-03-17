package org.connectus;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import com.firebase.client.Firebase;
import org.connectus.model.GmailThread;

import javax.inject.Inject;

public class ResidentThreadListActivity extends Activity {

    public static final String RESIDENT_ID_ARG = "residentId";

    @Inject
    UserRepository userRepository;

    ListView messagesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.activity_login);

        String residentId = getIntent().getStringExtra(RESIDENT_ID_ARG);

        messagesListView = (ListView) findViewById(R.id.list_view_message);

        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentMessagesUrl(FirebaseFacade.encode(userRepository.getUserEmail()), residentId));
        ThreadAdapter adapter = new ThreadAdapter(this, GmailThread.class, R.layout.thread_list_item, ref);
        messagesListView.setAdapter(adapter);
    }
}
