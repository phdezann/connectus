package org.connectus;

import dagger.Component;
import org.connectus.dagger.AndroidModule;
import org.connectus.dagger.AppModule;
import org.connectus.dagger.ConnectusComponent;
import org.connectus.support.RobolectricTestBase;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import javax.inject.Inject;
import javax.inject.Singleton;

public class DateFormatterTest extends RobolectricTestBase {

    @Inject
    DateFormatter dateFormatter;

    private DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
    private DateTimeZone timeZone = DateTimeZone.forID("Europe/Paris");
    private DateTime now = parser.parseDateTime("2015-07-01T15:30:00+02:00");

    @Before
    public void setup() {
        ConnectusApplication connectusApplication = (ConnectusApplication) RuntimeEnvironment.application;
        ConnectusTestComponent component = DaggerDateFormatterTest_ConnectusTestComponent.builder().androidModule(new AndroidModule(connectusApplication)).build();
        connectusApplication.setupComponent(component);
        component.inject(this);
    }

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
        return dateFormatter.toPrettyString(dateFormatter.parse(date), now, timeZone);
    }

    @Component(modules = {AppModule.class, AndroidModule.class})
    @Singleton
    protected interface ConnectusTestComponent extends ConnectusComponent {
        void inject(DateFormatterTest dateFormatterTest);
    }
}
