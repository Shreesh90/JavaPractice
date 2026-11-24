package EmployeeChanges;

import java.time.LocalDate;
import java.util.*;

public class EmployeChangePortal {

    Map<String, Employee> employees = new HashMap<>();
    Map<String, Map<Field, TreeMap<LocalDate, List<ChangeEvent>>>> fieldsChangeHistory = new HashMap<>();
    Map<String, ChangeEvent> requestIdToSequence = new HashMap<>();
    long globalSequence;

    public EmployeChangePortal() {
        this.globalSequence=0;
    }

    public void addEmployee(String eID, LocalDate date, Map<Field, String> initialValues) {
        if(employees.containsKey(eID)) throw new IllegalArgumentException();

        employees.put(eID, new Employee(eID, initialValues, date));
        fieldsChangeHistory.computeIfAbsent(eID, k -> new HashMap<>());

        for(Map.Entry<Field, String> e: initialValues.entrySet()) {
            fieldsChangeHistory.get(eID)
                    .computeIfAbsent(e.getKey(), k -> new TreeMap<>())
                    .computeIfAbsent(date, k -> new ArrayList<>())
                    .add(new ChangeEvent(null, eID, e.getKey(), e.getValue(), date, date, ++globalSequence));
        }
    }

    public long scheduleChange(String requestId, String eID, Field field, String value, LocalDate effectiveDate, LocalDate today) {
        if(requestIdToSequence.containsKey(requestId)) {
            if(field != requestIdToSequence.get(requestId).field() ||
                    !value.equals(requestIdToSequence.get(requestId).value()) ||
                    !effectiveDate.equals(requestIdToSequence.get(requestId).effectiveDate())
            ) throw new IllegalArgumentException();
            else return requestIdToSequence.get(requestId).seq();
        }
        if(!employees.containsKey(eID)) throw new IllegalArgumentException();
        if(effectiveDate.isBefore(today) || effectiveDate.isBefore(employees.get(eID).getHireDate())) throw new IllegalArgumentException();

        ChangeEvent ce = new ChangeEvent(requestId, eID, field, value, effectiveDate, today, ++globalSequence);
        fieldsChangeHistory
                .computeIfAbsent(eID, k -> new HashMap<>())
                .computeIfAbsent(field, k -> new TreeMap<>())
                .computeIfAbsent(effectiveDate, k -> new ArrayList<>())
                .add(ce);

        requestIdToSequence.put(requestId,ce);
        return globalSequence;
    }

    public Map<Field, String> getSnapshot (String eID, LocalDate date) {
        if(!employees.containsKey(eID)) throw new IllegalArgumentException();
        if(date.isBefore(employees.get(eID).getHireDate())) throw new IllegalArgumentException();

        Map<Field, String> values = new HashMap<>();
        Map<Field, TreeMap<LocalDate, List<ChangeEvent>>> fieldMap = fieldsChangeHistory.get(eID);
        for(Field f: Field.values()) {
            if(!fieldMap.containsKey(f)) continue;
            ChangeEvent event = fieldMap.get(f).floorEntry(date).getValue().getLast();
            values.put(f, event.value());
        }
        return values;
    }

}
