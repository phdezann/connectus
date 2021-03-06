package org.connectus;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class ResidentAddDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ((ConnectusApplication) getActivity().getApplication()).getComponent().inject(this);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.resident_add_dialog_layout, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(dialogLayout);
        EditText nameEditText = (EditText) dialogLayout.findViewById(R.id.name);
        builder.setNegativeButton(android.R.string.cancel, (dialog, id) -> dismiss());
        builder.setPositiveButton(R.string.add, (dialog, id) -> {
            ((MainActivity) getActivity()).addResidentAndAddContact(nameEditText.getText().toString());
            dismiss();
        });
        AlertDialog dialog = builder.create();
        return dialog;
    }
}
