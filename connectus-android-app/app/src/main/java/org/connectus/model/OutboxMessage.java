package org.connectus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OutboxMessage {
    String residentId;
    String to;
    String threadId;
    String personal;
    String subject;
    String content;
}
