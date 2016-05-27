package org.connectus;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

public class MessageDialogFragment extends DialogFragment {

    public static final String MESSAGE_CONTENT = "message_content";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.message_dialog_layout, container, false);
        TextView content = (TextView) inflate.findViewById(R.id.content);
        content.setText(getArguments().getString(MESSAGE_CONTENT));
        content.setMovementMethod(new ScrollingMovementMethod());
        return inflate;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }
}
