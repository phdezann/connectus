package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.Message;
import org.connectus.model.Resident;

public class MessageAdapter extends FirebaseListAdapter<Message> {
    public MessageAdapter(Activity activity, Class<Message> modelClass, int modelLayout, Firebase ref) {
        super(activity, modelClass, modelLayout, ref);
    }

    @Override
    protected void populateView(View view, Message message, int position) {
        TextView subject = (TextView) view.findViewById(R.id.subject);
        TextView content = (TextView) view.findViewById(R.id.content);
        TextView resident = (TextView) view.findViewById(R.id.resident);

        subject.setText(StringUtils.abbreviate(message.getSubject(), 50));
        content.setText(StringUtils.abbreviate(message.getContent(), 50));
        Optional<Resident> residentOpt = message.getResident();
        if (residentOpt.isPresent()) {
            resident.setText(residentOpt.get().getName());
        }
    }
}
