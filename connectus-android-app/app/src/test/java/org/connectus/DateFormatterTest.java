package org.connectus;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateFormatterTest {

    private DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
    private DateTimeZone timeZone = DateTimeZone.forID("Europe/Paris");
    private DateTime now = parser.parseDateTime("2015-07-01T15:30:00+02:00");

    @Test
    public void toPrettyString() {
        assertThat(asPrettyString("2015-07-01T06:29:40-07:00", now)).isEqualTo("A l'instant");
        assertThat(asPrettyString("2015-07-01T06:29:00-07:00", now)).isEqualTo("Il y a 1 minute");
        assertThat(asPrettyString("2015-07-01T06:22:40-07:00", now)).isEqualTo("Il y a 8 minutes");
        assertThat(asPrettyString("2015-06-30T06:22:40-07:00", now)).isEqualTo("Hier à 15:22");
        assertThat(asPrettyString("2015-06-29T06:22:40-07:00", now)).isEqualTo("Le 29/06/15 à 15:22");
        assertThat(asPrettyString("2015-06-15T06:22:40-07:00", now)).isEqualTo("Le 15/06/15 à 15:22");
        assertThat(asPrettyString("2015-07-01T09:55:51Z[UTC]", now)).isEqualTo("11:55");
    }

    private String asPrettyString(String date, DateTime now) {
        return DateFormatter.toPrettyString(DateFormatter.parse(date), now, timeZone);
    }
}
