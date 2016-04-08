package org.connectus.model;

import com.google.common.base.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GmailThread {
    String id;
    String snippet;
    GmailMessage lastMessage;
    String contactEmail;

    public Optional<String> getContactEmailOpt() {
        return Optional.fromNullable(contactEmail);
    }
}
