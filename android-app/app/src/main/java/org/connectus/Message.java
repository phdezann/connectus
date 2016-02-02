package org.connectus;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Message {
    String subject;
    String content;
}
