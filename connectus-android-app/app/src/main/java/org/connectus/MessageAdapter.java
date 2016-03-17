package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.GmailMessage;

public class MessageAdapter extends FirebaseListAdapter<GmailMessage> {
    public MessageAdapter(Activity activity, Class<GmailMessage> modelClass, int modelLayout, Firebase ref) {
        super(activity, modelClass, modelLayout, ref);
    }

    @Override
    protected void populateView(View view, GmailMessage gmailMessage, int position) {
        TextView subject = (TextView) view.findViewById(R.id.subject);
        TextView content = (TextView) view.findViewById(R.id.content);
        TextView resident = (TextView) view.findViewById(R.id.resident);

        subject.setText(StringUtils.abbreviate(gmailMessage.getSubject(), 50));
        content.setText(StringUtils.abbreviate(gmailMessage.getContent(), 50));
        resident.setText(gmailMessage.getResidentOpt().transform(r -> r.getName()).or(""));
    }
}
