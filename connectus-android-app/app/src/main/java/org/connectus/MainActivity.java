package org.connectus;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
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

    ListView messagesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((ConnectusApplication) getApplication()).getComponent().inject(this);
        setContentView(R.layout.main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setTitle(userRepository.getUserEmail());
        toolbar.setSubtitle(R.string.admin_view);

        messagesListView = (ListView) findViewById(R.id.list_view_message);
        setupThreadAdapter();
    }

    public void addResidentAndAddContact(String residentName) {
        firebaseFacade.addResident(userRepository.getUserEmail(), residentName, Resident.deriveLabelName(residentName));
    }

    public void onAddContact(String emailOfContact, String residentId, Optional<String> previousBoundResidentId) {
        firebaseFacade.updateContact(userRepository.getUserEmail(), residentId, emailOfContact, previousBoundResidentId);
    }

    private void setupThreadAdapter() {
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
                    intent.putExtra(ResidentThreadListActivity.RESIDENT_NAME_ARG, resident.getName());
                    startActivity(intent);
                } else {
                    toaster.toast(getString(R.string.no_resident_associated));
                }
                return true;
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                logout();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
