package com.decerto.leszek.confitura2026.demo19_invoice;

import com.decerto.leszek.confitura2026.Demo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;

/**
 * The same domain model two ways - an invoice with line items, each with a quantity, a unit price, a
 * tax rate and a delivery date - built 10,000 times with 10 items each. A: mutable classes with
 * wrapper types, as a JPA entity would look. B: immutable records (JDK 16 style). Run plain for the
 * pre-Valhalla baseline, then with preview to see what the JDK's own value classes (Integer, Long,
 * LocalDate) do to the very same code. ValueInvoiceModels adds model C on top.
 *
 * <pre>
 * ./run.sh    InvoiceModels
 * ./run.sh -p InvoiceModels
 * </pre>
 */
public class InvoiceModels {

  public static final int INVOICES = 10_000;
  public static final int ITEMS = 10;
  public static final String[] SKUS = {"A-100", "B-200", "C-300", "D-400", "E-500"};
  public static final LocalDate DAY = LocalDate.of(2026, 9, 20);

  // ---- A: classic mutable classes -------------------------------------------------------------

  public static class ClassicMoney {
    long cents;
    int currency;

    public long cents() { return cents; }

    public int currency() { return currency; }
  }

  public static class ClassicTaxRate {
    short basisPoints;

    public short basisPoints() { return basisPoints; }
  }

  public static class ClassicLineItem {
    String sku;
    Integer quantity;
    ClassicMoney unitNet;
    ClassicTaxRate taxRate;
    LocalDate deliveredOn;

    public String sku() { return sku; }

    public Integer quantity() { return quantity; }

    public ClassicMoney unitNet() { return unitNet; }

    public ClassicTaxRate taxRate() { return taxRate; }

    public LocalDate deliveredOn() { return deliveredOn; }
  }

  public static class ClassicCountryCode {
    short iso;

    public short iso() { return iso; }
  }

  public static class ClassicPhone {
    long e164;

    public long e164() { return e164; }
  }

  public static class ClassicAddress {
    String street;
    String city;
    String postalCode;
    ClassicCountryCode country;

    public String street() { return street; }

    public String city() { return city; }

    public String postalCode() { return postalCode; }

    public ClassicCountryCode country() { return country; }
  }

  public static class ClassicCustomer {
    Long id;
    String name;
    String vatId;
    LocalDate since;
    Integer loyaltyPoints;
    ClassicAddress address;
    ClassicPhone phone;

    public Long id() { return id; }

    public String name() { return name; }

    public String vatId() { return vatId; }

    public LocalDate since() { return since; }

    public Integer loyaltyPoints() { return loyaltyPoints; }

    public ClassicAddress address() { return address; }

    public ClassicPhone phone() { return phone; }
  }

  public static class ClassicInvoice {
    Long id;
    ClassicCustomer customer;
    LocalDate issuedOn;
    LocalDate dueOn;
    List<ClassicLineItem> items = new ArrayList<>(ITEMS);

    public Long id() { return id; }

    public ClassicCustomer customer() { return customer; }

    public LocalDate issuedOn() { return issuedOn; }

    public LocalDate dueOn() { return dueOn; }

    public List<ClassicLineItem> items() { return items; }
  }

  public static ClassicInvoice classic(int i) {
    var invoice = new ClassicInvoice();
    invoice.id = (long) i;
    invoice.customer = classicCustomer(i);
    invoice.issuedOn = DAY;
    invoice.dueOn = DAY.plusDays(14);
    for (var j = 0; j < ITEMS; j++) {
      var item = new ClassicLineItem();
      item.sku = SKUS[j % SKUS.length];
      item.quantity = j + 1;
      item.unitNet = new ClassicMoney();
      item.unitNet.cents = 1999L + j;
      item.unitNet.currency = 985;
      item.taxRate = new ClassicTaxRate();
      item.taxRate.basisPoints = 2300;
      item.deliveredOn = DAY.plusDays(j);
      invoice.items.add(item);
    }
    return invoice;
  }

  static ClassicCustomer classicCustomer(int i) {
    var customer = new ClassicCustomer();
    customer.id = 100_000L + i;
    customer.name = "Ada Lovelace";
    customer.vatId = "PL1234567890";
    customer.since = DAY.minusYears(3);
    customer.loyaltyPoints = i % 1000;
    customer.address = new ClassicAddress();
    customer.address.street = "Analytical Engine St. 1";
    customer.address.city = "London";
    customer.address.postalCode = "SW1A 1AA";
    customer.address.country = new ClassicCountryCode();
    customer.address.country.iso = 826;
    customer.phone = new ClassicPhone();
    customer.phone.e164 = 44_20_7946_0000L + i;
    return customer;
  }

  public static long classicGross(ClassicInvoice invoice) {
    var total = 0L;
    for (var item : invoice.items) {
      total += item.quantity * item.unitNet.cents * (10_000 + item.taxRate.basisPoints) / 10_000;
    }
    return total;
  }

  // ---- B: records ------------------------------------------------------------------------------

  public record RMoney(long cents, int currency) {}

  public record RTaxRate(short basisPoints) {}

  public record RLineItem(String sku, int quantity, RMoney unitNet, RTaxRate taxRate, LocalDate deliveredOn) {}

  public record RCountryCode(short iso) {}

  public record RPhone(long e164) {}

  public record RAddress(String street, String city, String postalCode, RCountryCode country) {}

  public record RCustomer(long id, String name, String vatId, LocalDate since, int loyaltyPoints, RAddress address, RPhone phone) {}

  public record RInvoice(long id, RCustomer customer, LocalDate issuedOn, LocalDate dueOn, List<RLineItem> items) {}

  public static RInvoice records(int i) {
    var items = new ArrayList<RLineItem>(ITEMS);
    for (var j = 0; j < ITEMS; j++) {
      items.add(new RLineItem(SKUS[j % SKUS.length], j + 1, new RMoney(1999L + j, 985), new RTaxRate((short) 2300), DAY.plusDays(j)));
    }
    return new RInvoice(i, recordCustomer(i), DAY, DAY.plusDays(14), items);
  }

  static RCustomer recordCustomer(int i) {
    return new RCustomer(100_000L + i, "Ada Lovelace", "PL1234567890", DAY.minusYears(3), i % 1000,
        new RAddress("Analytical Engine St. 1", "London", "SW1A 1AA", new RCountryCode((short) 826)), new RPhone(44_20_7946_0000L + i));
  }

  public static long recordsGross(RInvoice invoice) {
    var total = 0L;
    for (var item : invoice.items()) {
      total += item.quantity() * item.unitNet().cents() * (10_000 + item.taxRate().basisPoints()) / 10_000;
    }
    return total;
  }

  // ---- measurement -----------------------------------------------------------------------------

  public static void main(String[] args) {
    Demo.section("shapes of one line item");
    Demo.shape(classic(0).items.get(0));
    Demo.shape(records(0).items().get(0));

    Demo.section("shapes of one customer");
    Demo.shape(classic(0).customer);
    Demo.shape(records(0).customer());

    Demo.section("shapes of one invoice (without its items)");
    Demo.shape(classic(0));
    Demo.shape(records(0));

    header();
    measure("A classic classes", InvoiceModels::classic, InvoiceModels::classicGross);
    measure("B records", InvoiceModels::records, InvoiceModels::recordsGross);
  }

  public static void header() {
    Demo.section(INVOICES + " invoices x " + ITEMS + " items");
    System.out.printf("  %-28s %14s %14s %10s%n", "model", "allocated", "per invoice", "sum gross");
  }

  public static <T> void measure(String name, IntFunction<T> build, ToLongFunction<T> gross) {
    var warmUp = IntStream.range(0, 3 * INVOICES).mapToObj(build).mapToLong(gross).sum(); // let C2 settle
    var before = Demo.allocatedBytes();
    var invoices = new ArrayList<T>(INVOICES);
    for (var i = 0; i < INVOICES; i++) {
      invoices.add(build.apply(i));
    }
    var allocated = Demo.allocatedBytes() - before;
    var total = 0L;
    for (var invoice : invoices) {
      total += gross.applyAsLong(invoice);
    }
    System.out.printf("  %-28s %,14d %,14d %,10d%s%n", name, allocated, allocated / INVOICES, total, warmUp == total * 3 ? "" : " (!)");
  }
}
