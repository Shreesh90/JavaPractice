package DriverDeliverySystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Driver {
    public String driverId;

    TreeMap<Long, Long> driverRates = new TreeMap<>();
    List<Delivery> deliveries = new ArrayList<>();

    public Driver(String driverId, long driverRate) {
        this.driverId = driverId;
        this.driverRates.put(Long.MIN_VALUE, driverRate);
    }

    public void addDelivery(Delivery delivery) {
        deliveries.add(delivery);
    }

    public void updateRate(long time, long rate){
        if(driverRates.lastKey() >= time) throw new IllegalArgumentException();
        driverRates.put(time, rate);
    }

    public long getRateAt(long time) {
        return driverRates.floorEntry(time).getValue();
    }

    // Cent-seconds for [start, end), split at every rate change strictly inside it
    public long costFor(long start, long end) {
        long cost = 0;
        long cursor = start;
        long rate = getRateAt(start);
        for (Map.Entry<Long, Long> change : driverRates.subMap(start, false, end, false).entrySet()) {
            cost += (change.getKey() - cursor) * rate;
            cursor = change.getKey();
            rate = change.getValue();
        }
        return cost + (end - cursor) * rate;
    }

}
