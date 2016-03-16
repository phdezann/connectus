package org.connectus.model;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@Data
public class Message {
    String from;
    String subject;
    String content;
    Map<String, String> labels = Maps.newHashMap();
}
