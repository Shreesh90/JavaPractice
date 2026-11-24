package EmployeeChanges;

import java.time.LocalDate;

public record ChangeEvent(String requestId, String eId, Field field, String value, LocalDate effectiveDate, LocalDate createdDate, long seq) {

    // Recorded after it took effect: payroll must back-pay
    public boolean isRetroactive() {
        return effectiveDate.isBefore(createdDate);
    }
}
