package EmployeeChanges;

import java.time.LocalDate;
import java.util.Map;

public class Employee {
    private String eId;
    private LocalDate hire;
    Map<Field, String> values;

    public Employee(String eID, Map<Field, String> values, LocalDate hire) {
        this.eId = eID;
        this.values = values;
        this.hire = hire;
    }

    public LocalDate getHireDate() {
        return this.hire;
    }
}
