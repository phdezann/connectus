package org.connectus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.google.common.base.Optional;
import lombok.extern.slf4j.Slf4j;
import org.connectus.model.GmailThread;
import org.connectus.model.Resident;

import javax.inject.Inject;

@Slf4j
public class MainActivity extends ActivityBase {

    @Inject
    LoginOrchestrator loginOrchestrator;

    TextView connectedUser;
    ListView messagesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.main);

        connectedUser = (TextView) findViewById(R.id.connected_user);
        connectedUser.setText(userRepository.getUserEmail());

        findViewById(R.id.logout).setOnClickListener(view -> logout());
        messagesListView = (ListView) findViewById(R.id.list_view_message);
        setupMessageAdapter();
    }

    public void addResidentAndAddContact(String residentName) {
        firebaseFacade.addResident(userRepository.getUserEmail(), residentName, Resident.deriveLabelName(residentName));
    }

    public void onAddContact(String emailOfContact, String residentId, Optional<String> previousBoundResidentId) {
        firebaseFacade.updateContact(userRepository.getUserEmail(), residentId, emailOfContact, previousBoundResidentId);
    }

    private void setupMessageAdapter() {
        if (userRepository.isUserLoggedIn()) {
            Firebase ref = new Firebase(FirebaseFacadeConstants.getAdminMessagesUrl(FirebaseFacade.encode(userRepository.getUserEmail())));
            ThreadAdapter adapter = new ThreadAdapter(this, GmailThread.class, R.layout.thread_list_item, ref);
            messagesListView.setAdapter(adapter);

            messagesListView.setOnItemClickListener((parent, view, position, id) -> {
                GmailThread thread = adapter.getItem(position);
                ResidentListDialogFragment fragment = new ResidentListDialogFragment();
                Bundle args = new Bundle();
                args.putString(ResidentListDialogFragment.CONTACT_EMAIL_ARG, thread.getContactEmailOpt().get());
                args.putString(ResidentListDialogFragment.BOUND_RESIDENT_ID_ARG, thread.getLastMessage().getResidentOpt().transform(r -> r.getId()).orNull());
                fragment.setArguments(args);

                fragment.show(getFragmentManager(), ResidentListDialogFragment.class.getSimpleName());
            });

            messagesListView.setOnItemLongClickListener((parent, view, position, id) -> {
                GmailThread thread = adapter.getItem(position);
                Optional<Resident> residentOpt = thread.getLastMessage().getResidentOpt();
                if (residentOpt.isPresent()) {
                    Resident resident = residentOpt.get();
                    Intent intent = new Intent(this, ResidentThreadListActivity.class);
                    intent.putExtra(ResidentThreadListActivity.RESIDENT_ID_ARG, resident.getId());
                    startActivity(intent);
                } else {
                    toaster.toast(getString(R.string.no_resident_associated));
                }
                return true;
            });
        }
    }
}
