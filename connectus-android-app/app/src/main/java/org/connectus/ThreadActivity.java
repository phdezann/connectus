package org.connectus;

import android.app.Activity;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.firebase.client.Firebase;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import org.connectus.model.GmailMessage;
import rx.android.schedulers.AndroidSchedulers;

import javax.inject.Inject;

public class ThreadActivity extends Activity {

    public static final String RESIDENT_ID_ARG = "residentId";
    public static final String THREAD_ID_ARG = "threadId";

    @Inject
    UserRepository userRepository;
    @Inject
    FirebaseFacade firebaseFacade;
    @Inject
    Toaster toaster;

    MessageAdapter adapter;
    ListView messagesListView;
    Optional<GmailMessage> inboundMessage = Optional.absent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.thread);

        LinearLayout replyLayout = (LinearLayout) findViewById(R.id.reply_layout);
        EditText messageEditText = (EditText) findViewById(R.id.message_edit);
        Button sendButton = (Button) findViewById(R.id.send_btn);

        String residentId = getIntent().getStringExtra(RESIDENT_ID_ARG);
        String threadId = getIntent().getStringExtra(THREAD_ID_ARG);
        messagesListView = (ListView) findViewById(R.id.list_view_message);

        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentMessagesOfThreadUrl(FirebaseFacade.encode(userRepository.getUserEmail()), residentId, threadId));
        adapter = new MessageAdapter(this, GmailMessage.class, ref, userRepository, firebaseFacade, threadId);
        messagesListView.setAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                inboundMessage = findInboundMessage();
                replyLayout.setVisibility(inboundMessage.isPresent() ? View.VISIBLE : View.INVISIBLE);
            }
        });

        sendButton.setOnClickListener(view -> {
            String to = inboundMessage.get().getFrom();
            GmailMessage item = adapter.getItem(0);
            String subject = item.getSubject();
            String content = messageEditText.getText().toString();
            String personal = item.getResidentOpt().transform(r -> r.getName()).or("");

            // TODO add to future activityBase
            firebaseFacade.addOutboxMessage(FirebaseFacade.encode(userRepository.getUserEmail()), residentId, to, threadId, personal, subject, content) //
                    .observeOn(AndroidSchedulers.mainThread()) //
                    .subscribe(noOp -> finish(), e -> {
                        e.printStackTrace();
                        toaster.toast("Error: " + Throwables.getStackTraceAsString(e));
                    });
        });
    }

    private Optional<GmailMessage> findInboundMessage() {
        int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            GmailMessage item = adapter.getItem(i);
            if (!item.getFrom().equals(userRepository.getUserEmail())) {
                return Optional.of(item);
            }
        }
        return Optional.absent();
    }
}
