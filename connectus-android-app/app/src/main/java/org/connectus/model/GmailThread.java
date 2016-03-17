package org.connectus.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GmailThread {
    String id;
    String snippet;
    GmailMessage lastMessage;
}
