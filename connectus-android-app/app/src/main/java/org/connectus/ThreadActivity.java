package org.connectus;

import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.firebase.client.Firebase;
import com.firebase.client.Query;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import org.connectus.model.GmailMessage;
import rx.android.schedulers.AndroidSchedulers;

import javax.inject.Inject;

public class ThreadActivity extends ActivityBase {

    public static final String RESIDENT_ID_ARG = "residentId";
    public static final String THREAD_ID_ARG = "threadId";
    public static final String CONTACT_EMAIL_ARG = "contactEmail";
    private static final String REPLY_LAYOUT_OPENED = "replyLayoutOpened";

    @Inject
    UserRepository userRepository;
    @Inject
    Repository repository;
    @Inject
    Toaster toaster;

    MessageAdapter adapter;
    ListView messagesListView;
    Optional<GmailMessage> inboundMessage = Optional.absent();
    boolean replyLayoutOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.thread);

        Button reply_btn = (Button) findViewById(R.id.reply_btn);
        LinearLayout replyLayout = (LinearLayout) findViewById(R.id.reply_layout);
        EditText messageEditText = (EditText) findViewById(R.id.message_edit);
        Button sendButton = (Button) findViewById(R.id.send_btn);

        String residentId = getIntent().getStringExtra(RESIDENT_ID_ARG);
        String threadId = getIntent().getStringExtra(THREAD_ID_ARG);
        String contactEmail = getIntent().getStringExtra(CONTACT_EMAIL_ARG);
        messagesListView = (ListView) findViewById(R.id.list_view_message);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (savedInstanceState != null) {
            replyLayoutOpened = savedInstanceState.getBoolean(REPLY_LAYOUT_OPENED);
        }

        reply_btn.setOnClickListener(view -> {
            replyLayoutOpened = true;
            updateReplyLayout(reply_btn, replyLayout);
        });
        updateReplyLayout(reply_btn, replyLayout);

        toolbar.setTitle(String.format(getString(R.string.thread_with), contactEmail));

        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentMessagesOfThreadUrl(userRepository.getUserEmail(), residentId, threadId));
        Query orderByDate = ref.orderByChild(FirebaseFacadeConstants.MESSAGE_DATE_PATH);
        adapter = new MessageAdapter(this, GmailMessage.class, orderByDate, userRepository, repository, threadId);
        messagesListView.setAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                inboundMessage = findInboundMessage();
            }
        });

        sendButton.setOnClickListener(view -> {
            String to = inboundMessage.get().getFrom();
            GmailMessage item = adapter.getItem(0);
            String subject = item.getSubject();
            String content = messageEditText.getText().toString();
            String personal = item.getResidentOpt().transform(r -> r.getName()).or("");

            subs.add(repository.addOutboxMessage(userRepository.getUserEmail(), residentId, to, threadId, personal, subject, content) //
                    .observeOn(AndroidSchedulers.mainThread()) //
                    .subscribe(noOp -> finish(), e -> {
                        e.printStackTrace();
                        toaster.toast("Error: " + Throwables.getStackTraceAsString(e));
                    }));
        });
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REPLY_LAYOUT_OPENED, replyLayoutOpened);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void updateReplyLayout(Button reply_btn, LinearLayout replyLayout) {
        if (replyLayoutOpened) {
            reply_btn.setVisibility(View.GONE);
            replyLayout.setVisibility(View.VISIBLE);
        } else {
            reply_btn.setVisibility(View.VISIBLE);
            replyLayout.setVisibility(View.GONE);
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        adapter.cleanup();
    }
}
