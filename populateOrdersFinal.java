private void populateDates(OrderSearchRequest orderSearchRequest) {
        if (orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.ALL.getValue())) {
            orderSearchRequest.setFromDate("");
            orderSearchRequest.setToDate("");
        } else if (orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.TODAY.getValue())) {
            orderSearchRequest.setFromDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DateUtility.DATE_FORMAT_MMDDYYYY)));
            orderSearchRequest.setToDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DateUtility.DATE_FORMAT_MMDDYYYY)));
        }
        else if(orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.THIS_WEEK.getValue()))
        {
		LocalDate today=LocalDate.now();
        	LocalDate monday = today;
    		while (monday.getDayOfWeek() != DayOfWeek.MONDAY)
    		{
     	 		monday = monday.minusDays(1);
    		}

		orderSearchRequest.setFromDate(monday.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
		orderSearchRequest.setToDate("");

        }
	else if(orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.THIS_MONTH.getValue()))
        {
		
		orderSearchRequest.setFromDate(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("MM/dd/yyyy")););
		orderSearchRequest.setToDate("");

        }

    }