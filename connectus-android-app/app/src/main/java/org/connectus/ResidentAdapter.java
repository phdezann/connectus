package org.connectus;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.firebase.client.Firebase;
import com.firebase.ui.FirebaseListAdapter;
import com.google.common.base.Optional;
import org.connectus.model.Resident;

public class ResidentAdapter extends FirebaseListAdapter<Resident> {

    private Optional<String> boundResidentId;

    public ResidentAdapter(Activity activity, Class<Resident> modelClass, int modelLayout, Firebase ref, Optional<String> boundResidentId) {
        super(activity, modelClass, modelLayout, ref);
        this.boundResidentId = boundResidentId;
    }

    @Override
    protected void populateView(View view, Resident resident, int position) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(resident.getName());
        if (getRef(position).getKey().equals(boundResidentId.orNull())) {
            name.setText(name.getText().toString() + "*");
        }
    }
}
