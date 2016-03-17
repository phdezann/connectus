package org.connectus.model;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.connectus.FirebaseFacadeConstants;

import java.util.Map;

@NoArgsConstructor
@Data
public class Message {
    String from;
    String subject;
    String content;
    Map<String, String> labels = Maps.newHashMap();
    Map<String, String> resident = Maps.newHashMap();

    public Optional<Resident> getResident() {
        if (!resident.isEmpty()) {
            String id = resident.get(FirebaseFacadeConstants.RESIDENT_ID_PROPERTY);
            String name = resident.get(FirebaseFacadeConstants.RESIDENT_NAME_PROPERTY);
            String labelName = resident.get(FirebaseFacadeConstants.RESIDENT_LABEL_NAME_PROPERTY);
            return Optional.of(new Resident(id, name, labelName, null));
        }
        return Optional.absent();
    }
}
