package org.connectus.model;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Map;

@NoArgsConstructor
@Data
public class GmailMessage {
    String from;
    String subject;
    String content;
    String date;
    Resident resident;
    Map<String, String> labels = Maps.newHashMap();

    public DateTime getParsedDate() {
        DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
        return parser.parseDateTime(date);
    }

    public Optional<Resident> getResidentOpt() {
        return Optional.fromNullable(resident);
    }

    public boolean isSent() {
        return labels.containsKey("SENT");
    }
}
