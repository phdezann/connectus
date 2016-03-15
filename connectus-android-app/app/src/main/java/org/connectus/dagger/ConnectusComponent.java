package org.connectus.dagger;

import org.connectus.ConnectusApplication;
import org.connectus.MainActivity;
import org.connectus.ResidentAddDialogFragment;
import org.connectus.ResidentListDialogFragment;

public interface ConnectusComponent {
    void inject(ConnectusApplication admvApplication);

    void inject(MainActivity mainActivity);

    void inject(ResidentListDialogFragment residentListDialogFragment);

    void inject(ResidentAddDialogFragment residentAddDialogFragment);
}
