package org.connectus;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.GmailMessage;

public class MessageAdapter extends FirebaseListAdapter<GmailMessage> {

    private static final int LEFT_TYPE = 0;
    private static final int RIGHT_TYPE = 1;

    private Activity activity;

    public MessageAdapter(Activity activity, Class<GmailMessage> modelClass, Firebase ref) {
        super(activity, modelClass, 0, ref);
        this.activity = activity;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        View inflate = activity.getLayoutInflater().inflate(getViewResourceFromType(position), viewGroup, false);
        populateView(inflate, getItem(position), position);
        return inflate;
    }

    private int getViewResourceFromType(int position) {
        switch (getItemViewType(position)) {
            case LEFT_TYPE:
                return R.layout.thread_list_item_left;
            case RIGHT_TYPE:
                return R.layout.thread_list_item_right;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public int getItemViewType(int position) {
        GmailMessage item = getItem(position);
        if (item.isSent()) {
            return RIGHT_TYPE;
        } else {
            return LEFT_TYPE;
        }
    }

    @Override
    protected void populateView(View view, GmailMessage gmailMessage, int position) {
        TextView subject = (TextView) view.findViewById(R.id.date);
        TextView content = (TextView) view.findViewById(R.id.content);

        subject.setText(DateFormatter.toPrettyString(gmailMessage.getParsedDate()));
        content.setText(StringUtils.abbreviate(gmailMessage.getContent(), 50));
    }
}
