package com.decerto.leszek.confitura2026.demo19_invoice;

import static com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels.DAY;
import static com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels.ITEMS;
import static com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels.SKUS;

import com.decerto.leszek.confitura2026.Demo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Model C on top of InvoiceModels: the same records, with value records for the small immutable
 * things (money, tax rate, line item). The invoice itself stays an identity record - it is the
 * aggregate root you would track, cache or lock. Its items stay in a List: a List cannot be flat,
 * and neither could a VLineItem[] (an element is far over the 64-bit array limit), so each item is
 * one buffered heap object - with everything it owns inline.
 *
 * <pre>
 * ./run.sh -p ValueInvoiceModels
 * </pre>
 */
public class ValueInvoiceModels {

  public value record VMoney(long cents, int currency) {}

  public value record VTaxRate(short basisPoints) {}

  public value record VLineItem(String sku, int quantity, VMoney unitNet, VTaxRate taxRate, LocalDate deliveredOn) {}

  public value record VCountryCode(short iso) {}

  public value record VPhone(long e164) {}

  public value record VAddress(String street, String city, String postalCode, VCountryCode country) {}

  /** The customer *as billed* - a snapshot, so a value; the live customer entity lives elsewhere. */
  public value record VCustomer(long id, String name, String vatId, LocalDate since, int loyaltyPoints, VAddress address, VPhone phone) {}

  public record VInvoice(long id, VCustomer customer, LocalDate issuedOn, LocalDate dueOn, List<VLineItem> items) {}

  public static VInvoice values(int i) {
    var items = new ArrayList<VLineItem>(ITEMS);
    for (var j = 0; j < ITEMS; j++) {
      items.add(new VLineItem(SKUS[j % SKUS.length], j + 1, new VMoney(1999L + j, 985), new VTaxRate((short) 2300), DAY.plusDays(j)));
    }
    return new VInvoice(i, valueCustomer(i), DAY, DAY.plusDays(14), items);
  }

  static VCustomer valueCustomer(int i) {
    return new VCustomer(100_000L + i, "Ada Lovelace", "PL1234567890", DAY.minusYears(3), i % 1000,
        new VAddress("Analytical Engine St. 1", "London", "SW1A 1AA", new VCountryCode((short) 826)), new VPhone(44_20_7946_0000L + i));
  }

  public static long valuesGross(VInvoice invoice) {
    var total = 0L;
    for (var item : invoice.items()) {
      total += item.quantity() * item.unitNet().cents() * (10_000 + item.taxRate().basisPoints()) / 10_000;
    }
    return total;
  }

  public static void main(String[] args) {
    Demo.section("shapes of one line item");
    Demo.shape(InvoiceModels.classic(0).items.get(0));
    Demo.shape(InvoiceModels.records(0).items().get(0));
    Demo.shape(values(0).items().get(0));

    Demo.section("shapes of one customer");
    Demo.shape(InvoiceModels.classic(0).customer);
    Demo.shape(InvoiceModels.records(0).customer());
    Demo.shape(values(0).customer());

    Demo.section("shapes of one invoice (without its items)");
    Demo.shape(InvoiceModels.classic(0));
    Demo.shape(InvoiceModels.records(0));
    Demo.shape(values(0));

    InvoiceModels.header();
    InvoiceModels.measure("A classic classes", InvoiceModels::classic, InvoiceModels::classicGross);
    InvoiceModels.measure("B records", InvoiceModels::records, InvoiceModels::recordsGross);
    InvoiceModels.measure("C records + value records", ValueInvoiceModels::values, ValueInvoiceModels::valuesGross);
  }
}
