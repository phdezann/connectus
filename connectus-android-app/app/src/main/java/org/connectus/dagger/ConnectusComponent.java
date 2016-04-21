package org.connectus.dagger;

import org.connectus.*;

public interface ConnectusComponent {
    void inject(ConnectusApplication admvApplication);

    void inject(MainActivity mainActivity);

    void inject(ResidentListDialogFragment residentListDialogFragment);

    void inject(ResidentAddDialogFragment residentAddDialogFragment);

    void inject(ResidentThreadListActivity residentThreadListActivity);

    void inject(ThreadActivity threadActivity);

    void inject(AttachmentHttpAdapter attachmentHttpAdapter);

    void inject(LoginActivity loginActivity);

    void inject(ActivityBase activityBase);

    void inject(MessageAdapter messageAdapter);
}
