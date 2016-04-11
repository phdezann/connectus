package org.connectus;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

public class DateFormatter {

    public static DateTime parse(String date) {
        return ISODateTimeFormat.dateTimeParser().parseDateTime(pruneZoneIdIfAny(date));
    }

    private static String pruneZoneIdIfAny(String date) {
        return StringUtils.substringBefore(date, "[");
    }

    public static String toPrettyString(DateTime date) {
        return toPrettyString(date, DateTime.now(), DateTimeZone.getDefault());
    }

    public static String toPrettyString(DateTime date, DateTime now, DateTimeZone timeZone) {
        DateTime dateInDefaultTimeZone = date.toDateTime(timeZone);
        DateTime nowInDefaultTimeZone = now.toDateTime(timeZone);
        DateTime dateMinusOneMinute = nowInDefaultTimeZone.minusMinutes(1);
        DateTime dateMinusOneHour = nowInDefaultTimeZone.minusHours(1);
        DateTime startOfToday = nowInDefaultTimeZone.withTimeAtStartOfDay();
        DateTime startOfYesterday = nowInDefaultTimeZone.minusDays(1).withTimeAtStartOfDay();

        if (dateInDefaultTimeZone.isAfter(dateMinusOneMinute)) {
            return "A l'instant";
        } else if (dateInDefaultTimeZone.isAfter(dateMinusOneHour)) {
            int minutes = nowInDefaultTimeZone.getMinuteOfDay() - dateInDefaultTimeZone.getMinuteOfDay();
            return "Il y a " + minutes + " minute" + (minutes == 1 ? "" : "s");
        } else if (dateInDefaultTimeZone.isAfter(startOfToday)) {
            return dateInDefaultTimeZone.toString("HH:mm");
        } else if (dateInDefaultTimeZone.isAfter(startOfYesterday)) {
            return "Hier à " + dateInDefaultTimeZone.toString("HH:mm");
        } else {
            return dateInDefaultTimeZone.toString("'Le' dd/MM/YY 'à' HH:mm");
        }
    }
}
