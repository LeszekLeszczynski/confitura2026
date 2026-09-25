package com.decerto.leszek.confitura2026.demo21_json;

import com.decerto.leszek.confitura2026.Demo;
import com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels;
import com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels.ClassicInvoice;
import com.decerto.leszek.confitura2026.demo19_invoice.ValueInvoiceModels;
import com.decerto.leszek.confitura2026.demo19_invoice.ValueInvoiceModels.VInvoice;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.IntFunction;

/**
 * The invoice as a REST API would return it: serialized to JSON with Jackson, and read back. Does
 * it work at all on value records, and how does the memory saved in the model compare with what
 * one serialization allocates?
 *
 * <pre>
 * ./run.sh -p JacksonInvoice
 * </pre>
 */
@SuppressWarnings("unchecked")
public class JacksonInvoice {

  static final int ROUNDS = 10_000;

  static final ObjectMapper MAPPER = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .visibility(PropertyAccessor.FIELD, Visibility.ANY) // the classic model has no getters
      .build();

  public static void main(String[] args) throws Exception {
    System.out.println("Jackson " + MAPPER.version());
    var classic = InvoiceModels.classic(1);
    var value = ValueInvoiceModels.values(1);

    Demo.section("does it work?");
    var classicJson = MAPPER.writeValueAsString(classic);
    var valueJson = MAPPER.writeValueAsString(value);
    System.out.println("  ClassicInvoice -> " + classicJson.length() + " chars: " + classicJson.substring(0, 160) + "...");
    System.out.println("  VInvoice       -> " + valueJson.length() + " chars: " + valueJson.substring(0, 160) + "...");
    System.out.println("  same JSON tree: " + MAPPER.readTree(classicJson).equals(MAPPER.readTree(valueJson)));
    var back = MAPPER.readValue(valueJson, VInvoice.class);
    System.out.println("  VInvoice round-trip: read back " + back.getClass().getSimpleName() + ", equals(original): " + back.equals(value)
        + ", customer == original.customer: " + (back.customer() == value.customer()));
    var classicBack = MAPPER.readValue(classicJson, ClassicInvoice.class);
    System.out.println("  ClassicInvoice round-trip: gross " + InvoiceModels.classicGross(classicBack) + " vs " + InvoiceModels.classicGross(classic));

    Demo.section("bytes allocated per invoice, after warm-up (" + ROUNDS + " rounds)");
    System.out.printf("  %-16s %14s %14s %14s%n", "model", "build model", "serialize", "deserialize");
    report("ClassicInvoice", InvoiceModels::classic, ClassicInvoice.class);
    report("VInvoice", ValueInvoiceModels::values, VInvoice.class);

    Demo.section("same, but serialized by hand-written streaming code (no reflection, as a build-time generated serializer would)");
    System.out.println("  streaming JSON equals databind JSON: "
        + MAPPER.readTree(streaming(classic, JacksonInvoice::write)).equals(MAPPER.readTree(classicJson)) + " / "
        + MAPPER.readTree(streaming(value, JacksonInvoice::write)).equals(MAPPER.readTree(valueJson)));
    System.out.printf("  %-16s %14s%n", "model", "serialize");
    reportStreaming("ClassicInvoice", InvoiceModels::classic, JacksonInvoice::write);
    reportStreaming("VInvoice", ValueInvoiceModels::values, JacksonInvoice::write);
  }

  interface Writer<T> {
    void write(JsonGenerator g, T value) throws IOException;
  }

  static final ByteArrayOutputStream OUT = new ByteArrayOutputStream(4096); // reused, like a servlet response buffer

  static <T> byte[] streaming(T value, Writer<T> writer) throws IOException {
    OUT.reset();
    try (var g = MAPPER.createGenerator(OUT)) {
      writer.write(g, value);
    }
    return OUT.toByteArray();
  }

  static <T> int streamingSize(T value, Writer<T> writer) throws IOException {
    OUT.reset();
    try (var g = MAPPER.createGenerator(OUT)) {
      writer.write(g, value);
    }
    return OUT.size();
  }

  static <T> void reportStreaming(String name, IntFunction<T> build, Writer<T> writer) throws IOException {
    var invoices = new Object[ROUNDS];
    for (var i = 0; i < ROUNDS; i++) {
      invoices[i] = build.apply(i);
    }
    for (var round = 0; round < 3; round++) {
      for (var invoice : invoices) {
        streamingSize((T) invoice, writer);
      }
    }
    var before = Demo.allocatedBytes();
    var size = 0;
    for (var invoice : invoices) {
      size = streamingSize((T) invoice, writer);
    }
    System.out.printf("  %-16s %,14d   (JSON: %,d bytes)%n", name, (Demo.allocatedBytes() - before) / ROUNDS, size);
  }

  // --- what a generated serializer looks like: direct accessor calls, no Object in between ----------

  static void write(JsonGenerator g, ClassicInvoice invoice) throws IOException {
    g.writeStartObject();
    g.writeNumberField("id", invoice.id());
    g.writeFieldName("customer");
    g.writeStartObject();
    var c = invoice.customer();
    g.writeNumberField("id", c.id());
    g.writeStringField("name", c.name());
    g.writeStringField("vatId", c.vatId());
    g.writeStringField("since", c.since().toString());
    g.writeNumberField("loyaltyPoints", c.loyaltyPoints());
    g.writeFieldName("address");
    g.writeStartObject();
    g.writeStringField("street", c.address().street());
    g.writeStringField("city", c.address().city());
    g.writeStringField("postalCode", c.address().postalCode());
    g.writeFieldName("country");
    g.writeStartObject();
    g.writeNumberField("iso", c.address().country().iso());
    g.writeEndObject();
    g.writeEndObject();
    g.writeFieldName("phone");
    g.writeStartObject();
    g.writeNumberField("e164", c.phone().e164());
    g.writeEndObject();
    g.writeEndObject();
    g.writeStringField("issuedOn", invoice.issuedOn().toString());
    g.writeStringField("dueOn", invoice.dueOn().toString());
    g.writeArrayFieldStart("items");
    for (var item : invoice.items()) {
      g.writeStartObject();
      g.writeStringField("sku", item.sku());
      g.writeNumberField("quantity", item.quantity());
      g.writeFieldName("unitNet");
      g.writeStartObject();
      g.writeNumberField("cents", item.unitNet().cents());
      g.writeNumberField("currency", item.unitNet().currency());
      g.writeEndObject();
      g.writeFieldName("taxRate");
      g.writeStartObject();
      g.writeNumberField("basisPoints", item.taxRate().basisPoints());
      g.writeEndObject();
      g.writeStringField("deliveredOn", item.deliveredOn().toString());
      g.writeEndObject();
    }
    g.writeEndArray();
    g.writeEndObject();
  }

  static void write(JsonGenerator g, VInvoice invoice) throws IOException {
    g.writeStartObject();
    g.writeNumberField("id", invoice.id());
    g.writeFieldName("customer");
    g.writeStartObject();
    var c = invoice.customer();
    var address = c.address();
    var country = address.country();
    var phone = c.phone();
    g.writeNumberField("id", c.id());
    g.writeStringField("name", c.name());
    g.writeStringField("vatId", c.vatId());
    g.writeStringField("since", c.since().toString());
    g.writeNumberField("loyaltyPoints", c.loyaltyPoints());
    g.writeFieldName("address");
    g.writeStartObject();
    g.writeStringField("street", address.street());
    g.writeStringField("city", address.city());
    g.writeStringField("postalCode", address.postalCode());
    g.writeFieldName("country");
    g.writeStartObject();
    g.writeNumberField("iso", country.iso());
    g.writeEndObject();
    g.writeEndObject();
    g.writeFieldName("phone");
    g.writeStartObject();
    g.writeNumberField("e164", phone.e164());
    g.writeEndObject();
    g.writeEndObject();
    g.writeStringField("issuedOn", invoice.issuedOn().toString());
    g.writeStringField("dueOn", invoice.dueOn().toString());
    g.writeArrayFieldStart("items");
    for (var item : invoice.items()) {
      var unitNet = item.unitNet();
      var taxRate = item.taxRate();
      g.writeStartObject();
      g.writeStringField("sku", item.sku());
      g.writeNumberField("quantity", item.quantity());
      g.writeFieldName("unitNet");
      g.writeStartObject();
      g.writeNumberField("cents", unitNet.cents());
      g.writeNumberField("currency", unitNet.currency());
      g.writeEndObject();
      g.writeFieldName("taxRate");
      g.writeStartObject();
      g.writeNumberField("basisPoints", taxRate.basisPoints());
      g.writeEndObject();
      g.writeStringField("deliveredOn", item.deliveredOn().toString());
      g.writeEndObject();
    }
    g.writeEndArray();
    g.writeEndObject();
  }


  static <T> void report(String name, IntFunction<T> build, Class<T> type) throws Exception {
    var invoices = new Object[ROUNDS];
    var json = new byte[ROUNDS][];
    for (var round = 0; round < 3; round++) { // warm-up
      for (var i = 0; i < ROUNDS; i++) {
        invoices[i] = build.apply(i);
        json[i] = MAPPER.writeValueAsBytes(invoices[i]);
        MAPPER.readValue(json[i], type);
      }
    }
    var before = Demo.allocatedBytes();
    for (var i = 0; i < ROUNDS; i++) {
      invoices[i] = build.apply(i);
    }
    var buildBytes = (Demo.allocatedBytes() - before) / ROUNDS;
    before = Demo.allocatedBytes();
    for (var i = 0; i < ROUNDS; i++) {
      json[i] = MAPPER.writeValueAsBytes(invoices[i]);
    }
    var serializeBytes = (Demo.allocatedBytes() - before) / ROUNDS;
    before = Demo.allocatedBytes();
    for (var i = 0; i < ROUNDS; i++) {
      invoices[i] = MAPPER.readValue(json[i], type);
    }
    var deserializeBytes = (Demo.allocatedBytes() - before) / ROUNDS;
    System.out.printf("  %-16s %,14d %,14d %,14d   (JSON: %,d bytes)%n", name, buildBytes, serializeBytes, deserializeBytes, json[0].length);
  }
}
