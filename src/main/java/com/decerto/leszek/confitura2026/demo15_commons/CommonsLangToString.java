package com.decerto.leszek.confitura2026.demo15_commons;

import com.decerto.leszek.confitura2026.Demo;
import java.time.LocalDate;
import java.util.WeakHashMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * A real library, unmodified: commons-lang3's ToStringBuilder detects cycles by registering every
 * value it visits in a WeakHashMap - so it fails on any boxed number or date, whether it found the
 * value by reflection or you handed it over yourself. Same code, run plain and with preview.
 *
 * <pre>
 * ./run.sh    CommonsLangToString
 * ./run.sh -p CommonsLangToString
 * </pre>
 */
public class CommonsLangToString {

  static class Order {
    Long id = 1000L;
    Integer net = 1000;
    Integer gross = 1000;
    LocalDate created = LocalDate.of(2026, 9, 19);
    LocalDate updated = LocalDate.of(2026, 9, 19);

    /** Exactly what an IDE generates when you pick "toString() with ToStringBuilder". */
    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("id", id)
          .append("net", net)
          .append("created", created)
          .toString();
    }
  }

  /** Nothing value-typed in sight - just primitives and a String. */
  static class Plain {
    int count = 3;
    long total = 1000;
    String name = "demo";
  }

  public static void main(String[] args) {
    System.out.println("commons-lang3 " + ReflectionToStringBuilder.class.getPackage().getImplementationVersion());
    var order = new Order();
    var plain = new Plain();

    Demo.section("ReflectionToStringBuilder");
    attempt("Order (Integer, LocalDate fields)", () -> ReflectionToStringBuilder.toString(order, ToStringStyle.SHORT_PREFIX_STYLE));
    attempt("Plain (int, long, String fields) ", () -> ReflectionToStringBuilder.toString(plain, ToStringStyle.SHORT_PREFIX_STYLE));

    Demo.section("ToStringBuilder in a hand-written toString() - no reflection at all");
    attempt("append(\"id\", Long)          -> append(String, Object)", () -> new ToStringBuilder(order).append("id", order.id).toString());
    attempt("append(\"created\", LocalDate) -> append(String, Object)", () -> new ToStringBuilder(order).append("created", order.created).toString());
    attempt("append(\"name\", String)       -> append(String, Object)", () -> new ToStringBuilder(plain).append("name", plain.name).toString());
    attempt("append(\"total\", long)        -> primitive overload", () -> new ToStringBuilder(plain).append("total", plain.total).toString());
    attempt("the whole hand-written toString()", order::toString);

    Demo.section("the other reflective builders");
    attempt("EqualsBuilder.reflectionEquals   ", () -> String.valueOf(EqualsBuilder.reflectionEquals(order, new Order())));
    attempt("HashCodeBuilder.reflectionHashCode", () -> String.valueOf(HashCodeBuilder.reflectionHashCode(order)));

    Demo.section("root cause: ToStringStyle.register() -> WeakHashMap.put(value)");
    attempt("new WeakHashMap<>().put(1000L, null)", () -> {
      new WeakHashMap<Object, Object>().put(1000L, null);
      return "ok";
    });
  }

  interface Operation {
    String run();
  }

  static void attempt(String what, Operation operation) {
    String outcome;
    try {
      outcome = operation.run();
    } catch (RuntimeException e) {
      var frame = java.util.Arrays.stream(e.getStackTrace())
          .filter(f -> f.getClassName().startsWith("org.apache.commons"))
          .findFirst()
          .map(f -> "  @ " + f.getClassName().replaceAll(".*\\.", "") + "." + f.getMethodName() + ":" + f.getLineNumber())
          .orElse("");
      outcome = e.getClass().getSimpleName() + ": " + e.getMessage() + frame;
    }
    System.out.printf("  %-36s -> %s%n", what, outcome);
  }
}
