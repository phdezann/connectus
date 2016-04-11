package org.connectus.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GmailAttachment {
    String mimeType;
    String filename;
    int bodySize;
    String bodyAttachmentId;
}
