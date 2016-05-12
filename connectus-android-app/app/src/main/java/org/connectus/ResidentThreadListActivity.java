package org.connectus;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;
import com.firebase.client.Firebase;
import com.google.common.base.Optional;
import org.connectus.model.GmailThread;
import org.connectus.model.Resident;

import javax.inject.Inject;

public class ResidentThreadListActivity extends ActivityBase {

    public static final String RESIDENT_ID_ARG = "residentId";
    public static final String RESIDENT_NAME_ARG = "residentName";

    @Inject
    UserRepository userRepository;

    ListView messagesListView;
    ThreadAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.main);

        String residentId = getIntent().getStringExtra(RESIDENT_ID_ARG);
        String residentName = getIntent().getStringExtra(RESIDENT_NAME_ARG);

        messagesListView = (ListView) findViewById(R.id.list_view_message);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setTitle(String.format(getString(R.string.inbox_of), residentName));

        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentMessagesUrl(userRepository.getUserEmail(), residentId));
        adapter = new ThreadAdapter(this, GmailThread.class, R.layout.thread_list_item, ref);
        messagesListView.setAdapter(adapter);

        messagesListView.setOnItemClickListener((parent, view, position, id) -> {
            GmailThread thread = adapter.getItem(position);
            Optional<Resident> residentOpt = thread.getLastMessage().getResidentOpt();
            Resident resident = residentOpt.get();
            Intent intent = new Intent(this, ThreadActivity.class);
            intent.putExtra(ThreadActivity.RESIDENT_ID_ARG, resident.getId());
            intent.putExtra(ThreadActivity.THREAD_ID_ARG, thread.getId());
            intent.putExtra(ThreadActivity.CONTACT_EMAIL_ARG, thread.getContactEmail());
            startActivity(intent);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        adapter.cleanup();
    }
}
