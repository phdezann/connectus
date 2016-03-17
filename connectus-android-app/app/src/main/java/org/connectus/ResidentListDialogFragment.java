package org.connectus;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import com.firebase.client.Firebase;
import com.google.common.base.Optional;
import org.connectus.model.Resident;

import javax.inject.Inject;

public class ResidentListDialogFragment extends DialogFragment {

    public static final String BOUND_RESIDENT_ID_ARG = "residentId";
    @Inject
    UserRepository userRepository;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ((ConnectusApplication) getActivity().getApplication()).getComponent().inject(this);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.resident_dialog_layout, null);

        Optional<String> residentId = Optional.fromNullable(getArguments().getString(BOUND_RESIDENT_ID_ARG));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(dialogLayout);
        ListView listView = (ListView) dialogLayout.findViewById(R.id.residents);

        String encode = FirebaseFacade.encode(userRepository.getUserEmail());
        Firebase ref = new Firebase(FirebaseFacadeConstants.getResidentsUrl(encode));
        ResidentAdapter adapter = new ResidentAdapter(getActivity(), Resident.class, R.layout.resident_list_item, ref, residentId);
        listView.setAdapter(adapter);

        Button addResidentButton = (Button) dialogLayout.findViewById(R.id.add_resident);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ((MainActivity) getActivity()).onAddContact(adapter.getRef(position).getKey(), residentId);
            dismiss();
        });

        AlertDialog dialog = builder.create();
        addResidentButton.setOnClickListener(view -> {
            new ResidentAddDialogFragment().show(getFragmentManager(), ResidentAddDialogFragment.class.getSimpleName());
            dismiss();
        });
        return dialog;
    }
}
