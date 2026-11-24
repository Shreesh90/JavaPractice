package EmployeeChanges;

import java.time.LocalDate;
import java.util.*;

public class EmployeChangePortal {

    Map<String, Employee> employees = new HashMap<>();
    Map<String, Map<Field, TreeMap<LocalDate, List<ChangeEvent>>>> fieldsChangeHistory = new HashMap<>();
    Map<String, ChangeEvent> requestIdToSequence = new HashMap<>();
    TreeMap<LocalDate, Map<String, ChangeEvent>> salaryChanges = new TreeMap<>();

    long globalSequence;

    public EmployeChangePortal() {
        this.globalSequence=0;
    }

    public void addEmployee(String eID, LocalDate date, Map<Field, String> initialValues) {
        if (eID == null || eID.isBlank()) throw new IllegalArgumentException("employeeId cannot be blank");
        if (date == null) throw new IllegalArgumentException("hireDate cannot be null for " + eID);
        if (initialValues == null || initialValues.isEmpty())
            throw new IllegalArgumentException("initialValues cannot be empty for " + eID);
        if(employees.containsKey(eID)) throw new IllegalArgumentException("Employee already exists: " + eID);

        employees.put(eID, new Employee(eID, initialValues, date));
        fieldsChangeHistory.computeIfAbsent(eID, k -> new HashMap<>());

        for(Map.Entry<Field, String> e: initialValues.entrySet()) {
            ChangeEvent ce = new ChangeEvent(null, eID, e.getKey(), e.getValue(), date, date, ++globalSequence);
            fieldsChangeHistory.get(eID)
                    .computeIfAbsent(e.getKey(), k -> new TreeMap<>())
                    .computeIfAbsent(date, k -> new ArrayList<>())
                    .add(ce);
            if(e.getKey().equals(Field.SALARY)) {
                salaryChanges
                        .computeIfAbsent(date, k -> new HashMap<>())
                        .put(eID, ce);
            }
        }
    }

    public long scheduleChange(String requestId, String eID, Field field, String value, LocalDate effectiveDate, LocalDate today) {
        if (requestId == null || field == null || effectiveDate == null || today == null)
            throw new IllegalArgumentException("requestId, field, effectiveDate and today are required");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value cannot be blank");
        if(requestIdToSequence.containsKey(requestId)) {
            if(!eID.equals(requestIdToSequence.get(requestId).eId()) ||
                    field != requestIdToSequence.get(requestId).field() ||
                    !value.equals(requestIdToSequence.get(requestId).value()) ||
                    !effectiveDate.equals(requestIdToSequence.get(requestId).effectiveDate())
            ) throw new IllegalArgumentException("requestId reused with different payload: " + requestId);
            else return requestIdToSequence.get(requestId).seq();
        }
        if(!employees.containsKey(eID)) throw new IllegalArgumentException("Unknown employee: " + eID);
        if(effectiveDate.isBefore(today)) throw new IllegalArgumentException("effectiveDate is in the past: " + effectiveDate);
        if(effectiveDate.isBefore(employees.get(eID).getHireDate()))
            throw new IllegalArgumentException("effectiveDate is before hire date: " + effectiveDate);

        ChangeEvent ce = new ChangeEvent(requestId, eID, field, value, effectiveDate, today, ++globalSequence);
        fieldsChangeHistory
                .computeIfAbsent(eID, k -> new HashMap<>())
                .computeIfAbsent(field, k -> new TreeMap<>())
                .computeIfAbsent(effectiveDate, k -> new ArrayList<>())
                .add(ce);
        if(field.equals(Field.SALARY)) {
            salaryChanges
                    .computeIfAbsent(effectiveDate, k -> new HashMap<>())
                    .put(eID, ce);
        }

        requestIdToSequence.put(requestId,ce);
        return globalSequence;
    }

    public Map<Field, String> getSnapshot (String eID, LocalDate date) {
        if(!employees.containsKey(eID)) throw new IllegalArgumentException("Unknown employee: " + eID);
        if(date.isBefore(employees.get(eID).getHireDate()))
            throw new IllegalArgumentException("Employee " + eID + " not employed on " + date);

        Map<Field, String> values = new HashMap<>();
        Map<Field, TreeMap<LocalDate, List<ChangeEvent>>> fieldMap = fieldsChangeHistory.get(eID);
        for(Field f: Field.values()) {
            if(!fieldMap.containsKey(f)) continue;
            ChangeEvent event = fieldMap.get(f).floorEntry(date).getValue().getLast();
            values.put(f, event.value());
        }
        return values;
    }

    // Winning change per (field, future date): last in the same-day list
    public List<ChangeEvent> pendingChanges(String eID, LocalDate today) {
        if(!employees.containsKey(eID)) throw new IllegalArgumentException("Unknown employee: " + eID);
        List<ChangeEvent> pending = new ArrayList<>();
        for (TreeMap<LocalDate, List<ChangeEvent>> timeline : fieldsChangeHistory.get(eID).values()) {
            for (List<ChangeEvent> sameDay : timeline.tailMap(today, false).values()) {
                pending.add(sameDay.getLast());
            }
        }
        pending.sort(Comparator.comparing(ChangeEvent::effectiveDate).thenComparing(ChangeEvent::field));
        return pending;
    }

    // Resolve-on-read: same rule as getSnapshot, so it flips on the effective date with no job
    public List<String> employeesWithTitle(String title, LocalDate asOf) {
        List<String> result = new ArrayList<>();
        for (Employee employee : employees.values()) {
            if (asOf.isBefore(employee.getHireDate())) continue;
            TreeMap<LocalDate, List<ChangeEvent>> titles = fieldsChangeHistory.get(employee.getId()).get(Field.TITLE);
            if (titles != null && titles.floorEntry(asOf).getValue().getLast().value().equals(title)) {
                result.add(employee.getId());
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    List<ChangeEvent> drainDueNotifications(LocalDate date) {
        List<ChangeEvent> temp = new ArrayList<>();
        for(Map.Entry<LocalDate, Map<String, ChangeEvent>> e: salaryChanges.headMap(date, true).entrySet()) {
            Map<String, ChangeEvent> perEmployeeSalaryEvent = e.getValue() ;
            temp.addAll(perEmployeeSalaryEvent.values().stream().toList());
        }
        salaryChanges.headMap(date, true).clear();
        return temp;
    }

}
