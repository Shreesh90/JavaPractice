package EmployeeChanges;

import java.time.LocalDate;

public record ChangeEvent(String requestId, String eId, Field field, String value, LocalDate effectiveDate, LocalDate createdDate, long seq) {
}
