package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.GmailThread;

public class ThreadAdapter extends FirebaseListAdapter<GmailThread> {
    public ThreadAdapter(Activity activity, Class<GmailThread> modelClass, int modelLayout, Firebase ref) {
        super(activity, modelClass, modelLayout, ref);
    }

    @Override
    protected void populateView(View view, GmailThread thread, int position) {
        TextView snippet = (TextView) view.findViewById(R.id.snippet);
        TextView lastModification = (TextView) view.findViewById(R.id.last_modification);

        snippet.setText(StringUtils.abbreviate(thread.getId(), 50));
        lastModification.setText(StringUtils.abbreviate(thread.getLastMessage().getParsedDate().toString(), 50));
    }
}
