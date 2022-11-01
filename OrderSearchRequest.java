package com.shippingadaptor.dto;

import com.shippingadaptor.utility.DateUtility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrderSearchRequest {
    private String fromDate;
    private String toDate;
    private String timeFilter;
    private String fulfillmentStatus;
    private String storeMasterId;
    private String merchantStoreId;
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public LocalDateTime getFromLocalDate() {
        return LocalDateTime.of(DateUtility.stringToLocalDate(this.fromDate), LocalTime.MIDNIGHT);
    }

    public LocalDateTime getToLocalDate() {
         return LocalDateTime.of(DateUtility.stringToLocalDate(this.toDate), LocalTime.MAX);
    }

}
