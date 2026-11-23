package DriverDeliverySystem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class DriverDeliverySystem {
    Map<String, Driver> drivers = new HashMap<>();

    long totalCost;
    long totalUnpaidCost;
    HashSet<String> deliveryIds = new HashSet<>();
    PriorityQueue<Delivery> minHeapDeliveries = new PriorityQueue<>(Comparator.comparingLong(d -> d.endTime()));

    public DriverDeliverySystem() {
        totalCost = 0;
        totalUnpaidCost=0;
    }

    public void addDriver(String driverId, long rate) {
        if (rate <= 0) {
            throw new IllegalArgumentException("Rate cannot be negative: " + rate);
        }
        if (drivers.containsKey(driverId)) {
            throw new IllegalArgumentException("Driver already exists: " + driverId);
        }
        drivers.put(driverId, new Driver(driverId, rate));
    }

    public void recordDelivery(String deliveryId, String driverId, long startTime, long endTime) {
        Driver d = drivers.get(driverId);
        if(deliveryIds.contains(deliveryId)) return;
        if (d == null) {
            throw new IllegalArgumentException("Unknown driver: " + driverId);
        }
        if (startTime >= endTime) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        long driverCost = d.costFor(startTime, endTime);

        Delivery del = new Delivery(startTime, endTime, driverCost, deliveryId);
        deliveryIds.add(deliveryId);
        d.addDelivery(del);
        minHeapDeliveries.add(del);
        totalCost += driverCost;
        totalUnpaidCost += driverCost;
    }

    public void updateDriverRate(String driverId, long time, long rate) {
        Driver d = drivers.get(driverId);
        if (rate <= 0) {
            throw new IllegalArgumentException("Rate cannot be negative: " + rate);
        }
        if (d == null) {
            throw new IllegalArgumentException("driverId cannot be null: " + rate);
        }
        Objects.requireNonNull(d);
        d.updateRate(time, rate);
    }

    public BigDecimal getTotalCost() {
        return BigDecimal.valueOf(totalCost)
                .divide(BigDecimal.valueOf(360000), 2, RoundingMode.HALF_UP);
    }

    public void payUpTo(long time) {
        while(!minHeapDeliveries.isEmpty()) {
            if(minHeapDeliveries.peek().endTime() > time) break;
            totalUnpaidCost -= minHeapDeliveries.poll().cost();
        }
    }

    public BigDecimal getTotalCostUnpaid() {
        return BigDecimal.valueOf(totalUnpaidCost)
                .divide(BigDecimal.valueOf(360000), 2, RoundingMode.HALF_UP);
    }

    private static final long WINDOW_SECONDS = 86_400;

    private record Event(long time, int delta, String driverId) {}

    public int maxSimultaneousDriversInPast24Hours(long now) {
        long windowStart = now - WINDOW_SECONDS;
        List<Event> events = new ArrayList<>();
        for (Driver driver : drivers.values()) {
            for (Delivery d : driver.deliveries) {
                if (d.endTime() <= windowStart || d.startTime() > now) continue;
                events.add(new Event(Math.max(d.startTime(), windowStart), 1, driver.driverId));
                events.add(new Event(d.endTime(), -1, driver.driverId));
            }
        }
        // End before start at the same time: [s, e) intervals touching at T don't overlap
        events.sort(Comparator.comparingLong(Event::time).thenComparingInt(Event::delta));

        Map<String, Integer> activePerDriver = new HashMap<>();
        int active = 0;
        int max = 0;
        for (Event e : events) {
            int count = activePerDriver.merge(e.driverId(), e.delta(), Integer::sum);
            if (e.delta() == 1 && count == 1) {
                max = Math.max(max, ++active);
            } else if (e.delta() == -1 && count == 0) {
                active--;
            }
        }
        return max;
    }
}
