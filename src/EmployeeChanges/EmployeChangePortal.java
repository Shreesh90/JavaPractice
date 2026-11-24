package EmployeeChanges;

import java.time.LocalDate;
import java.util.*;

public class EmployeChangePortal {

    Map<String, Employee> employees = new HashMap<>();
    // <EmployeeId, <Field, <Date, List<ChangeEvent>>>
    Map<String, Map<Field, TreeMap<LocalDate, List<ChangeEvent>>>> fieldsChangeHistory = new HashMap<>();
    Map<String, ChangeEvent> requestIdToSequence = new HashMap<>();
    // <Date, <EmployeeID, ChangeEvent>>: stores latest salary change event in a particular date
    TreeMap<LocalDate, Map<String, ChangeEvent>> salaryChanges = new TreeMap<>();
    // <sequence number, event>
    Map<Long, ChangeEvent> scheduledBySeq = new HashMap<>();
    // <cancelled seq, new sequence generated>
    Map<Long, Long> cancelledBy = new HashMap<>();   // cancelled change's seq -> cancellation's seq

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
        // Part 3: past dates allowed (backdated); inserted mid-timeline, resolve-on-read picks it up
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
        scheduledBySeq.put(ce.seq(), ce);
        return globalSequence;
    }

    // Cancellation is a new event; the cancelled change stays in history
    public long cancelChange(long revision, LocalDate today) {
        if (today == null) throw new IllegalArgumentException("today is required");
        ChangeEvent target = scheduledBySeq.get(revision);
        if (target == null) throw new IllegalArgumentException("Unknown scheduled change: " + revision);
        if (cancelledBy.containsKey(revision)) return cancelledBy.get(revision);
        if (!target.effectiveDate().isAfter(today))
            throw new IllegalArgumentException("Change " + revision + " already effective; record a correction");

        cancelledBy.put(revision, ++globalSequence);
            if (target.field() == Field.SALARY) refreshOutbox(target);
        return globalSequence;
    }

    // If the cancelled change was the pending notification, fall back to the same-day winner or drop it
    private void refreshOutbox(ChangeEvent cancelled) {
        Map<String, ChangeEvent> due = salaryChanges.get(cancelled.effectiveDate());
        if (due == null || !cancelled.equals(due.get(cancelled.eId()))) return;
        List<ChangeEvent> sameDay = fieldsChangeHistory.get(cancelled.eId()).get(Field.SALARY).get(cancelled.effectiveDate());
        Optional<ChangeEvent> winner = lastActive(sameDay);
        if (winner.isPresent()) due.put(cancelled.eId(), winner.get());
        else due.remove(cancelled.eId());
        if (due.isEmpty()) salaryChanges.remove(cancelled.effectiveDate());
    }

    private Optional<ChangeEvent> lastActive(List<ChangeEvent> sameDay) {
        return lastActive(sameDay, Long.MAX_VALUE);
    }

    // Active as known at revision `known`: recorded by then, and not cancelled by then
    private Optional<ChangeEvent> lastActive(List<ChangeEvent> sameDay, long known) {
        for (ChangeEvent e : sameDay.reversed()) {
            if (e.seq() > known) continue;
            Long cancelSeq = cancelledBy.get(e.seq());
            if (cancelSeq == null || cancelSeq > known) return Optional.of(e);
        }
        return Optional.empty();
    }

    private Optional<ChangeEvent> resolve(TreeMap<LocalDate, List<ChangeEvent>> timeline, LocalDate asOf) {
        return resolve(timeline, asOf, Long.MAX_VALUE);
    }

    // The one rule: latest date <= asOf, last active change on that date; walk back if none
    private Optional<ChangeEvent> resolve(TreeMap<LocalDate, List<ChangeEvent>> timeline, LocalDate asOf, long known) {
        for (List<ChangeEvent> sameDay : timeline.headMap(asOf, true).descendingMap().values()) {
            Optional<ChangeEvent> winner = lastActive(sameDay, known);
            if (winner.isPresent()) return winner;
        }
        return Optional.empty();
    }

    public Map<Field, String> getSnapshot (String eID, LocalDate date) {
        return getSnapshot(eID, date, Long.MAX_VALUE);
    }

    // Bitemporal: what the system showed for `date` using only revisions <= knownAsOfRevision
    public Map<Field, String> getSnapshot (String eID, LocalDate date, long knownAsOfRevision) {
        if(!employees.containsKey(eID)) throw new IllegalArgumentException("Unknown employee: " + eID);
        if(date.isBefore(employees.get(eID).getHireDate()))
            throw new IllegalArgumentException("Employee " + eID + " not employed on " + date);

        Map<Field, String> values = new HashMap<>();
        Map<Field, TreeMap<LocalDate, List<ChangeEvent>>> fieldMap = fieldsChangeHistory.get(eID);
        for(Field f: Field.values()) {
            if(!fieldMap.containsKey(f)) continue;
            resolve(fieldMap.get(f), date, knownAsOfRevision).ifPresent(event -> values.put(f, event.value()));
        }
        return values;
    }

    // Winning change per (field, future date): last in the same-day list
    public List<ChangeEvent> pendingChanges(String eID, LocalDate today) {
        if(!employees.containsKey(eID)) throw new IllegalArgumentException("Unknown employee: " + eID);
        List<ChangeEvent> pending = new ArrayList<>();
        for (TreeMap<LocalDate, List<ChangeEvent>> timeline : fieldsChangeHistory.get(eID).values()) {
            for (List<ChangeEvent> sameDay : timeline.tailMap(today, false).values()) {
                lastActive(sameDay).ifPresent(pending::add);
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
            if (titles != null && resolve(titles, asOf).map(ChangeEvent::value).filter(title::equals).isPresent()) {
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
