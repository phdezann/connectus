package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import com.google.common.base.Optional;
import org.connectus.model.GmailThread;
import org.connectus.model.Resident;

import javax.inject.Inject;

public class ThreadAdapter extends FirebaseListAdapter<GmailThread> {

    @Inject
    DateFormatter dateFormatter;

    public ThreadAdapter(Activity activity, Class<GmailThread> modelClass, int modelLayout, Firebase ref) {
        super(activity, modelClass, modelLayout, ref);
        ((ConnectusApplication) activity.getApplication()).getComponent().inject(this);
    }

    @Override
    protected void populateView(View view, GmailThread thread, int position) {
        TextView id = (TextView) view.findViewById(R.id.id);
        TextView lastModification = (TextView) view.findViewById(R.id.last_modification);
        TextView snippet = (TextView) view.findViewById(R.id.snippet);
        TextView resident = (TextView) view.findViewById(R.id.resident);

        id.setText(thread.getId());
        lastModification.setText(dateFormatter.toPrettyString(thread.getLastMessage().getParsedDate()));
        snippet.setText(thread.getSnippet());

        Optional<Resident> residentOpt = thread.getLastMessage().getResidentOpt();
        if (residentOpt.isPresent() && resident != null) {
            resident.setText(residentOpt.get().getName());
        }
    }
}
