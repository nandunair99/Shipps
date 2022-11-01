package com.shippingadaptor.utility;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * DateUtility
 */
public class DateUtility {

    private DateUtility() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * DATE_FORMAT
     */
    public static final String DATE_FORMAT = "MMMM dd, yyyy hh:mm a";

    public static final String DATE_FORMAT_MMDDYYYY = "MM/dd/yyyy";

    /**
     * longToDate
     *
     * @param date
     * @return Returns {@link LocalDateTime}
     */
    public static LocalDateTime longToDate(Long date) {
        return Instant.ofEpochSecond(date).atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDate stringToLocalDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        return LocalDate.parse(date, formatter);
    }
}

