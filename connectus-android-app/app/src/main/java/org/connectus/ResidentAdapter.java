package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import org.connectus.model.Resident;

public class ResidentAdapter extends FirebaseListAdapter<Resident> {
    public ResidentAdapter(Activity activity, Class<Resident> modelClass, int modelLayout, Firebase ref) {
        super(activity, modelClass, modelLayout, ref);
    }

    @Override
    protected void populateView(View view, Resident resident, int position) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(resident.getName());
    }
}
