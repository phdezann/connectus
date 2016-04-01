package org.connectus;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import com.firebase.client.Firebase;
import org.connectus.model.GmailMessage;

import javax.inject.Inject;

public class ThreadActivity extends Activity {

    public static final String RESIDENT_ID_ARG = "residentId";
    public static final String THREAD_ID_ARG = "threadId";

    @Inject
    UserRepository userRepository;

    ListView messagesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.thread);

        String residentId = getIntent().getStringExtra(RESIDENT_ID_ARG);
        String threadId = getIntent().getStringExtra(THREAD_ID_ARG);
        messagesListView = (ListView) findViewById(R.id.list_view_message);

        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentMessagesOfThreadUrl(FirebaseFacade.encode(userRepository.getUserEmail()), residentId, threadId));
        MessageAdapter adapter = new MessageAdapter(this, GmailMessage.class, ref);
        messagesListView.setAdapter(adapter);
    }
}
