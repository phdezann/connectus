package org.connectus;

import android.app.Activity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import com.google.common.collect.FluentIterable;
import org.apache.commons.lang3.StringUtils;
import org.connectus.model.AttachmentFirebaseHttpRequest;
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
    private String threadId;

    public MessageAdapter(Activity activity, Class<GmailMessage> modelClass, Firebase ref, UserRepository userRepository, FirebaseFacade firebaseFacade, String threadId) {
        super(activity, modelClass, 0, ref);
        this.activity = activity;
        this.userRepository = userRepository;
        this.firebaseFacade = firebaseFacade;
        this.threadId = threadId;
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
        TextView date = (TextView) view.findViewById(R.id.date);
        TextView content = (TextView) view.findViewById(R.id.content);

        date.setText(DateFormatter.toPrettyString(gmailMessage.getParsedDate()));
        content.setText(StringUtils.abbreviate(gmailMessage.getContent(), 50));

        Firebase ref = getRef(position);
        String messageId = ref.getKey();

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.attachments);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));

        AttachmentHttpAdapter attachmentArrayAdapter = new AttachmentHttpAdapter(activity, threadId, messageId);
        recyclerView.setAdapter(attachmentArrayAdapter);

        if (!gmailMessage.getAttachments().isEmpty()) {
            recyclerView.setVisibility(View.VISIBLE);
            Observable<List<AttachmentFirebaseHttpRequest>> requests = firebaseFacade.getAttachmentRequests(FirebaseFacade.encode(userRepository.getUserEmail()), messageId);
            requests.subscribeOn(Schedulers.io()) //
                    .observeOn(AndroidSchedulers.mainThread()) //
                    .subscribe(ar -> {
                        List<AttachmentFirebaseHttpRequest> attachments = FluentIterable.from(ar).filter(p -> p.getMimeType().contains("image/")).toList();
                        attachmentArrayAdapter.setItems(attachments);
                        attachmentArrayAdapter.notifyDataSetChanged();
                    });
        }
    }
}
