package DriverDeliverySystem;

import java.util.ArrayList;
import java.util.List;
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

}
