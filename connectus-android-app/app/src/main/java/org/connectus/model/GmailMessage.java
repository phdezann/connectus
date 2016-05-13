package org.connectus.model;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

import java.util.Map;

@NoArgsConstructor
@Data
public class GmailMessage {
    String from;
    String subject;
    String content;
    long date;
    long reverseDate;
    Resident resident;
    Map<String, String> labels = Maps.newHashMap();
    Map<String, GmailAttachment> attachments = Maps.newHashMap();

    public DateTime getParsedDate() {
        return new DateTime(date);
    }

    public Optional<Resident> getResidentOpt() {
        return Optional.fromNullable(resident);
    }

    public boolean isSent() {
        return labels.containsKey("SENT");
    }
}
