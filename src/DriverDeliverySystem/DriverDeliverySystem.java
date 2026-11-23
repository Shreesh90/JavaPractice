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
        long deliveryDuration = endTime - startTime;
        long driverRate = d.getRateAt(startTime) ;
        long driverCost = deliveryDuration*driverRate;

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
}
