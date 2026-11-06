package DriverPaymentManagement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class DriverManagement {

    private static class Driver {
        final int driverId;
        TreeMap<Long, BigDecimal> rates = new TreeMap<>();

        public Driver(int driverId, BigDecimal rate) {
            this.driverId = driverId;
            rates.put(Long.MIN_VALUE, rate);
        }

        public void updateRate(long time, BigDecimal rate) {
            rates.put(time, rate);
        }

        public BigDecimal rateAt(long time) {
            return rates.floorEntry(time).getValue();
        }
    }

    private static class Delivery {
        long endTime;
        BigDecimal cost;
        int driverId;
        public Delivery(long endTime, BigDecimal cost, int driverId) {
            this.endTime = endTime;
            this.cost = cost;
            this.driverId = driverId;
        }
    }

    private final Map<Integer, Driver> drivers = new HashMap<>();
    private BigDecimal totalCost = BigDecimal.ZERO; // dollars
    private BigDecimal totalUnpaidCost = BigDecimal.ZERO; // dollars
    private PriorityQueue<Delivery> pq = new PriorityQueue<>(Comparator.comparingLong(d -> d.endTime));
    private List<Delivery> deliveries = new ArrayList<>();
    private static Integer SECONDS_PER_HOUR = 3600;

    public void addDriver(int driverId, double rate) {
        drivers.put(driverId, new Driver(driverId,BigDecimal.valueOf(rate)));
    }

    public void recordDelivery(int driverId, long startTime, long endTime) {
        BigDecimal driverRate = drivers.get(driverId).rateAt(startTime); // rate is Dollar per hour
        BigDecimal duration = BigDecimal.valueOf(endTime - startTime); // seconds
        BigDecimal deliveryCost = driverRate
                                    .multiply(duration)
                                    .divide(BigDecimal.valueOf(SECONDS_PER_HOUR), 10, RoundingMode.HALF_UP);
        Delivery d = new Delivery(endTime, deliveryCost, driverId);
        pq.add(d);
        deliveries.add(d);
        totalCost = totalCost.add(deliveryCost);
        totalUnpaidCost = totalUnpaidCost.add(deliveryCost);
    }

    public BigDecimal getTotalCost() {
        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    public void payUpTo(long time) {
        while(!pq.isEmpty()) {
            if(pq.peek().endTime > time) break;
            Delivery d = pq.poll();
            totalUnpaidCost = totalUnpaidCost.subtract(d.cost);
        }
    }

    public BigDecimal getTotalCostUnpaid() {
        return totalUnpaidCost.setScale(2, RoundingMode.HALF_UP);
    }

    public int getActiveDriversInLast24Hours(long time) {
        long t1 = time - 24L*3600;
        int l = 0, r = deliveries.size()-1;
        while(l<=r) {
            int mid = (l+r)/2;
            if(deliveries.get(mid).endTime <= t1) l = mid+1;
            else r = mid-1;
        }
        Set<Integer> drivers = new HashSet<>();
        for(int i=l;i<deliveries.size();i++) {
            if(deliveries.get(i).endTime > time) break;
            drivers.add(deliveries.get(i).driverId);
        }
        return drivers.size();
    }

    public void updateRate(int driverId, long newTime, double newRate) {
        Driver d = drivers.get(driverId);
        d.updateRate(newTime, BigDecimal.valueOf(newRate));
    }
}
