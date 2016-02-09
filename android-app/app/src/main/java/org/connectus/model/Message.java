package org.connectus.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Message {
    String from;
    String subject;
    String content;
}
