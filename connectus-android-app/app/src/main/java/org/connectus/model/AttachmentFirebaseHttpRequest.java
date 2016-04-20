package org.connectus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AttachmentFirebaseHttpRequest {
    String url;
    String accessToken;
    String mimeType;
}
