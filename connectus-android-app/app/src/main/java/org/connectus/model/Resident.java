package org.connectus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Resident {
    String id;
    String name;
    String labelName;
    String labelId;

    public static String deriveLabelName(String name) {
        return name.trim().replace(" ", "-").toLowerCase();
    }
}
