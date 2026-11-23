package DriverDeliverySystem;

public class Main {
    public static void main(String[] args) {
        DriverDeliverySystem system = new DriverDeliverySystem();
        system.addDriver("d1", 1000);
        system.addDriver("d2", 1250);
//        system.recordDelivery("d1", 0, 5400);
//        system.recordDelivery("d2", 3600, 5400);
//        print("basic total", "21.25", dollars(system.getTotalCost()));

        DriverDeliverySystem precision = new DriverDeliverySystem();
        precision.addDriver("d1", 1000);
        for (int i = 0; i < 3600; i++) {
//            precision.recordDelivery("d1", i, i + 1);
        }
//        print("precision trap", "10.00", dollars(precision.getTotalCost()));

        expectThrows("duplicate driver", () -> system.addDriver("d1", 500));
        // "d3" at 10.005 is not expressible: rate is already long cents
//        expectThrows("unknown driver", () -> system.recordDelivery("dX", 0, 60));
//        expectThrows("start >= end", () -> system.recordDelivery("d1", 100, 100));
        expectThrows("negative rate", () -> system.addDriver("d4", -100));

        DriverDeliverySystem rates = new DriverDeliverySystem();
        rates.addDriver("d1", 2000);
        rates.recordDelivery("a", "d1", 0, 5400);
        rates.updateDriverRate("d1", 7200, 3000);
        rates.recordDelivery("b", "d1", 3600, 9000);
        rates.recordDelivery("c", "d1", 7200, 8100);
        rates.recordDelivery("a", "d1", 0, 5400);
        print("worked example", "72.50", rates.getTotalCost().toPlainString()); // Part 5a: b is split at 7200

        Driver d1 = rates.drivers.get("d1");
        print("rate before change", "2000", String.valueOf(d1.getRateAt(7199)));
        print("rate at change", "3000", String.valueOf(d1.getRateAt(7200)));
        print("rate before any", "2000", String.valueOf(d1.getRateAt(-5)));

        expectThrows("same effective time", () -> rates.updateDriverRate("d1", 7200, 2500));
        expectThrows("backdated update", () -> rates.updateDriverRate("d1", 100, 2500));
        expectThrows("zero rate update", () -> rates.updateDriverRate("d1", 9000, 0));
        expectThrows("unknown driver rate", () -> rates.updateDriverRate("dX", 9000, 2500));
        expectThrows("dup id, bad data", () -> rates.recordDelivery("dup", "dX", 0, 60));
        expectOk("unused id reusable", () -> rates.recordDelivery("dup", "d1", 10000, 10060));

        DriverDeliverySystem pay = new DriverDeliverySystem();
        pay.addDriver("d1", 1000);
        pay.addDriver("d2", 2000);
        pay.recordDelivery("p1", "d1", 0, 3600);
        pay.recordDelivery("p2", "d2", 1800, 5400);
        print("unpaid before pay", "30.00", pay.getTotalCostUnpaid().toPlainString());
        pay.payUpTo(3600);
        print("ends at pay time", "20.00", pay.getTotalCostUnpaid().toPlainString());
        pay.payUpTo(3600);
        print("same time again", "20.00", pay.getTotalCostUnpaid().toPlainString());
        pay.payUpTo(1000);
        print("earlier time", "20.00", pay.getTotalCostUnpaid().toPlainString());
        pay.recordDelivery("p3", "d1", 7200, 9000);
        pay.payUpTo(8100);
        print("straddling unpaid", "5.00", pay.getTotalCostUnpaid().toPlainString());
        pay.recordDelivery("p4", "d2", 2000, 2600);
        print("late delivery", "8.33", pay.getTotalCostUnpaid().toPlainString());
        pay.payUpTo(8100);
        print("late paid next call", "5.00", pay.getTotalCostUnpaid().toPlainString());
        print("total unaffected", "38.33", pay.getTotalCost().toPlainString());
    }

    private static String dollars(long cents) {
        return String.format("%d.%02d", cents / 100, cents % 100);
    }

    private static void print(String name, String expected, String actual) {
        System.out.printf("%-18s expected=%-6s actual=%-6s %s%n", name, expected, actual,
                expected.equals(actual) ? "PASS" : "FAIL");
    }

    private static void expectThrows(String name, Runnable action) {
        try {
            action.run();
            System.out.printf("%-18s expected=IAE    actual=no exception FAIL%n", name);
        } catch (IllegalArgumentException e) {
            System.out.printf("%-18s expected=IAE    actual=IAE PASS%n", name);
        } catch (RuntimeException e) {
            System.out.printf("%-18s expected=IAE    actual=%s FAIL%n", name, e.getClass().getSimpleName());
        }
    }

    private static void expectOk(String name, Runnable action) {
        try {
            action.run();
            System.out.printf("%-18s expected=OK     actual=OK PASS%n", name);
        } catch (RuntimeException e) {
            System.out.printf("%-18s expected=OK     actual=%s FAIL%n", name, e.getClass().getSimpleName());
        }
    }
}
