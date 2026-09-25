package com.decerto.leszek.confitura2026.demo22_period_line;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.reflect.Field;
import java.time.LocalDate;

/**
 * Two classes with the same shape - a holder with two small immutable fields - and very different
 * outcomes. Period holds LocalDate, which the JDK turned into a 7-byte value class. IdentityLine
 * holds a plain class, which can never be inlined. ValuePeriodVsLine adds the third case: your own
 * value class Point(int, int), which is 8 bytes and therefore does NOT fit a reassignable field.
 *
 * <pre>
 * ./run.sh    PeriodVsLine        (nothing is a value class: the pre-Valhalla picture)
 * ./run.sh -p ValuePeriodVsLine   (all three side by side)
 * </pre>
 */
public class PeriodVsLine {

  /** Two java.time values in ordinary mutable fields - not a record, not a value class of ours. */
  public static class Period {
    LocalDate start;
    LocalDate end;

    public Period(int i) {
      start = LocalDate.of(2026, 9, 25).plusDays(i);
      end = start.plusDays(14);
    }
  }

  /** The identity twin of a value class Point: what the JVM can never inline. */
  public static class IdentityPoint {
    int x;
    int y;

    IdentityPoint(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  public static class IdentityLine {
    IdentityPoint start;
    IdentityPoint end;

    public IdentityLine(int i) {
      start = new IdentityPoint(i, i);
      end = new IdentityPoint(-i, -i);
    }
  }

  public static void main(String[] args) {
    System.out.println("LocalDate is a value class: " + LocalDate.class.isValue());

    header();
    report("Period      (2 x LocalDate)", new Period(1), "start", "end");
    report("IdentityLine(2 x class Point)", new IdentityLine(1), "start", "end");

    Demo.shape(new Period(1));
    Demo.shape(new IdentityLine(1));
  }

  public static void header() {
    Demo.section("holder with two small fields: what it costs");
    System.out.printf("  %-30s %8s %8s %10s %8s%n", "holder", "fields", "holder", "+ fields", "objects");
    System.out.printf("  %-30s %8s %8s %10s %8s%n", "", "flat?", "size", "= total", "");
  }

  /** Holder size plus every field that is still a reference to a separate heap object. */
  public static void report(String label, Object holder, String... fieldNames) {
    var type = holder.getClass();
    var total = Demo.sizeOf(holder);
    var objects = 1;
    var flat = true;
    for (var name : fieldNames) {
      if (Demo.isFlatField(type, name)) {
        continue;
      }
      flat = false;
      var value = read(type, name, holder);
      if (value != null) {
        total += Demo.sizeOf(value);
        objects++;
      }
    }
    System.out.printf("  %-30s %8s %6d B %8d B %8d%n", label, flat, Demo.sizeOf(holder), total, objects);
  }

  static Object read(Class<?> type, String name, Object instance) {
    try {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(instance);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
