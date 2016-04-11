package org.connectus;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import org.connectus.model.AttachmentHttpRequest;
import org.connectus.model.GmailMessage;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import java.util.List;

public class MessageAdapter extends FirebaseListAdapter<GmailMessage> {

    private static final int LEFT_TYPE = 0;
    private static final int RIGHT_TYPE = 1;

    private Activity activity;
    private FirebaseFacade firebaseFacade;
    private UserRepository userRepository;

    public MessageAdapter(Activity activity, Class<GmailMessage> modelClass, Firebase ref, UserRepository userRepository, FirebaseFacade firebaseFacade) {
        super(activity, modelClass, 0, ref);
        this.activity = activity;
        this.userRepository = userRepository;
        this.firebaseFacade = firebaseFacade;
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
        content.setText(gmailMessage.getAttachments().keySet().toString());

        Firebase ref = getRef(position);

        if (!gmailMessage.getAttachments().isEmpty()) {
            Observable<List<AttachmentHttpRequest>> requests = firebaseFacade.getAttachmentRequests(FirebaseFacade.encode(userRepository.getUserEmail()), ref.getKey());
            requests.subscribeOn(Schedulers.io()) //
                    .observeOn(AndroidSchedulers.mainThread()) //
                    .subscribe(ar -> System.out.println(ar));
        }
    }
}
