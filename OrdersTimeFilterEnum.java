package com.shippingadaptor.common.enums;

public enum OrdersTimeFilterEnum {
    ALL("All"),

    TODAY("Today"),

    THIS_WEEK("This Week"),

    THIS_MONTH("This Month"),

    CUSTOM_DATE_RANGE("Custom Date Range");

    private final String value;

    OrdersTimeFilterEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
