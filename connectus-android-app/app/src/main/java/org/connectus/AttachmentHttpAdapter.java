package org.connectus;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.common.collect.Lists;
import com.squareup.picasso.Picasso;
import lombok.NoArgsConstructor;
import org.connectus.model.AttachmentFirebaseHttpRequest;

import javax.inject.Inject;
import java.util.List;

import static org.connectus.PicassoBuilder.ACCESS_TOKEN_QUERY_PARAM;
import static org.connectus.PicassoBuilder.MIME_TYPE_QUERY_PARAM;

@NoArgsConstructor
public class AttachmentHttpAdapter extends RecyclerView.Adapter<AttachmentHttpAdapter.ListItemViewHolder> {

    Activity activity;
    @Inject
    Picasso picasso;

    private List<AttachmentFirebaseHttpRequest> items = Lists.newArrayList();
    private String threadId;
    private String messageId;

    public AttachmentHttpAdapter(Activity activity, String threadId, String messageId) {
        ((ConnectusApplication) activity.getApplication()).getComponent().inject(this);
        this.activity = activity;
        this.threadId = threadId;
        this.messageId = messageId;
    }

    public void setItems(List<AttachmentFirebaseHttpRequest> items) {
        this.items = items;
    }

    @Override
    public ListItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View itemView = LayoutInflater.
                from(viewGroup.getContext()).
                inflate(R.layout.attachment, viewGroup, false);
        return new ListItemViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ListItemViewHolder viewHolder, int position) {
        AttachmentFirebaseHttpRequest model = items.get(position);
        String url = String.format("%s?%s=%s&%s=%s", model.getUrl(), ACCESS_TOKEN_QUERY_PARAM, model.getAccessToken(), MIME_TYPE_QUERY_PARAM, model.getMimeType());
        String key = String.format("%s-%s-%s", threadId, messageId, position);
        picasso.load(url).stableKey(key).resizeDimen(R.dimen.attachment_width, R.dimen.attachment_height).centerInside().into(viewHolder.img);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public final static class ListItemViewHolder extends RecyclerView.ViewHolder {
        private ImageView img;

        public ListItemViewHolder(View itemView) {
            super(itemView);
            img = (ImageView) itemView.findViewById(R.id.attachment);
        }
    }
}
