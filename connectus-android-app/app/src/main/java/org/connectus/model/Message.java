package org.connectus.model;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
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
    Map<String, Resident> resident = Maps.newHashMap();

    public Optional<Resident> getResident() {
        return Optional.fromNullable(Iterables.getFirst(resident.values(), null));
    }
}
