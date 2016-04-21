package org.connectus;

import android.content.Context;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import javax.inject.Inject;

public class DateFormatter {

    public static final String TIME_PATTERN = "HH:mm";
    public static final String DATE_PATTERN = "dd/MM/YY";

    Context context;

    @Inject
    public DateFormatter(Context context) {
        this.context = context;
    }

    public DateTime parse(String date) {
        return ISODateTimeFormat.dateTimeParser().parseDateTime(pruneZoneIdIfAny(date));
    }

    private String pruneZoneIdIfAny(String date) {
        return StringUtils.substringBefore(date, "[");
    }

    public String toPrettyString(DateTime date) {
        return toPrettyString(date, DateTime.now(), DateTimeZone.getDefault());
    }

    public String toPrettyString(DateTime date, DateTime now, DateTimeZone timeZone) {
        DateTime dateInDefaultTimeZone = date.toDateTime(timeZone);
        DateTime nowInDefaultTimeZone = now.toDateTime(timeZone);
        DateTime dateMinusOneMinute = nowInDefaultTimeZone.minusMinutes(1);
        DateTime dateMinusOneHour = nowInDefaultTimeZone.minusHours(1);
        DateTime startOfToday = nowInDefaultTimeZone.withTimeAtStartOfDay();
        DateTime startOfYesterday = nowInDefaultTimeZone.minusDays(1).withTimeAtStartOfDay();

        if (dateInDefaultTimeZone.isAfter(dateMinusOneMinute)) {
            return context.getString(R.string.just_now);
        } else if (dateInDefaultTimeZone.isAfter(dateMinusOneHour)) {
            int minutes = nowInDefaultTimeZone.getMinuteOfDay() - dateInDefaultTimeZone.getMinuteOfDay();
            String minuteQuantity = context.getResources().getQuantityString(R.plurals.minute, minutes);
            return String.format(context.getString(R.string.minute_ago), minutes, minuteQuantity);
        } else if (dateInDefaultTimeZone.isAfter(startOfToday)) {
            return dateInDefaultTimeZone.toString(TIME_PATTERN);
        } else if (dateInDefaultTimeZone.isAfter(startOfYesterday)) {
            return String.format(context.getString(R.string.yesterday_at), dateInDefaultTimeZone.toString(TIME_PATTERN));
        } else {
            return String.format(context.getString(R.string.on_at), dateInDefaultTimeZone.toString(DATE_PATTERN), dateInDefaultTimeZone.toString(TIME_PATTERN));
        }
    }
}
