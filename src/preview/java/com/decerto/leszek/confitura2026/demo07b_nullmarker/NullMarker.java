package com.decerto.leszek.confitura2026.demo07b_nullmarker;

import com.decerto.leszek.confitura2026.Demo;
import jdk.internal.vm.annotation.NullRestricted;

/**
 * Where does the null marker byte come from, and what does it cost? A reference can be null for
 * free - the pointer has a spare value. A flat slot has no pointer: the bytes are the value, and
 * all-zero bytes are a perfectly good value, so one extra byte has to say "there is nothing here".
 * A null-restricted slot promises null can never happen, so it needs no byte at all.
 *
 * <pre>
 * ./run.sh -p NullMarker
 * ./run.sh -p NullMarker -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout
 * </pre>
 */
public class NullMarker {

  static final int N = 1_000_000;

  /** 8 bytes of payload: the interesting size - it flattens null-restricted, but not with a marker. */
  value record Money(int cents, int currency) {}

  // payloads chosen around the 8-byte boundary; each has an identical identity twin
  value record V7(int a, short b, byte c) {}

  record I7(int a, short b, byte c) {}

  value record V8(int x, int y) {}

  record I8(int x, int y) {}

  value record V12(long a, int b) {}

  record I12(long a, int b) {}

  value record V16(long a, long b) {}

  record I16(long a, long b) {}

  value record V64(long a, long b, long c, long d, long e, long f, long g, long h) {}

  record I64(long a, long b, long c, long d, long e, long f, long g, long h) {}

  value record Pixel(byte r, byte g, byte b, byte a) {}

  /** A record component is strict, so it flattens - and it is nullable, so it carries a marker. */
  record NullableHolder(Money money) {}

  /** Same value, promised never null: no marker byte. */
  static class NullFreeHolder {
    @NullRestricted final Money money;

    NullFreeHolder(Money money) {
      this.money = money;
      super();
    }
  }

  /** A plain class field: not flat at all (8 B payload + marker does not fit 64 bits), just a pointer. */
  static class ReferenceHolder {
    Money money = new Money(0, 0);
  }

  static NullableHolder holder = new NullableHolder(new Money(1, 985));
  static int sink;

  public static void main(String[] args) {
    Demo.section("1. why a marker is needed at all: zero is a real value");
    var zero = new Money(0, 0);
    System.out.println("  new Money(0, 0)        = " + zero + "   (8 bytes of zeros - a perfectly good value)");
    System.out.println("  new Money(0, 0) == null: " + (((Object) zero) == null) + "   so 'all zeros' cannot mean 'absent'");

    Demo.section("2. the cost on the heap: a buffered value carries the marker; an identity record does not");
    System.out.printf("  %-10s %10s %10s %8s%n", "payload", "value", "identity", "extra");
    buffered("7 bytes", new V7(1, (short) 2, (byte) 3), new I7(1, (short) 2, (byte) 3));
    buffered("8 bytes", new V8(1, 2), new I8(1, 2));
    buffered("12 bytes", new V12(1, 2), new I12(1, 2));
    buffered("16 bytes", new V16(1, 2), new I16(1, 2));
    buffered("64 bytes", new V64(1, 2, 3, 4, 5, 6, 7, 8), new I64(1, 2, 3, 4, 5, 6, 7, 8));
    System.out.println("  -> 8 extra bytes exactly when the payload fills its last 8-byte word, so the marker starts a new one");

    Demo.section("3. the three kinds of slot, same Money value");
    System.out.printf("  %-46s %-6s %-7s %8s %8s%n", "slot", "flat", "marker", "holder", "total");
    slot("Money money            (plain, reassignable field)", ReferenceHolder.class, "money", new ReferenceHolder());
    slot("record NullableHolder(Money)  (strict, nullable)", NullableHolder.class, "money", new NullableHolder(new Money(1, 985)));
    slot("@NullRestricted final Money    (strict, null-free)", NullFreeHolder.class, "money", new NullFreeHolder(new Money(1, 985)));
    Demo.shape(new NullableHolder(new Money(1, 985)));
    Demo.shape(new NullFreeHolder(new Money(1, 985)));

    Demo.section("4. what a null actually is: a null reference, not an object with the marker set");
    var empty = new NullableHolder(null);
    System.out.printf("  new NullableHolder(null).money() == null: %s%n", empty.money() == null);
    System.out.printf("  holder size with a value: %d B, with null: %d B  (identical - the slot exists either way)%n",
        Demo.sizeOf(new NullableHolder(new Money(1, 985))), Demo.sizeOf(empty));
    allocation("storing a value into the flat slot", () -> holder = new NullableHolder(new Money(1, 985)));
    allocation("storing null into the flat slot   ", () -> holder = new NullableHolder(null));
    System.out.println("  -> both allocate only the holder itself: null costs no object, and the value costs none either");

    Demo.section("5. the marker at scale: Pixel[" + N + "], 4 bytes of payload per element");
    var nullable = new Pixel[N];
    var nullFree = Demo.newNullRestrictedArray(Pixel.class, N, new Pixel((byte) 0, (byte) 0, (byte) 0, (byte) 0));
    System.out.printf("  new Pixel[N]                     %,10d bytes  %d bytes/element  (4 payload + 1 marker -> 8)%n",
        Demo.sizeOf(nullable), (Demo.sizeOf(nullable) - 16) / N);
    System.out.printf("  null-restricted Pixel[N]         %,10d bytes  %d bytes/element  (no marker, no rounding)%n",
        Demo.sizeOf(nullFree), (Demo.sizeOf(nullFree) - 16) / N);
  }

  static void buffered(String payload, Object value, Object identity) {
    var valueSize = Demo.sizeOf(value);
    var identitySize = Demo.sizeOf(identity);
    System.out.printf("  %-10s %9d B %9d B %7d B%n", payload, valueSize, identitySize, valueSize - identitySize);
  }

  static void slot(String what, Class<?> type, String field, Object instance) {
    var flat = Demo.isFlatField(type, field);
    var holder = Demo.sizeOf(instance);
    var total = holder + (flat ? 0 : Demo.sizeOf(new Money(1, 985))); // a non-flat slot points at a buffered value
    System.out.printf("  %-46s %-6s %-7s %6d B %6d B%s%n", what, flat, Demo.hasNullMarker(type, field), holder, total,
        flat ? " (one object)" : " (two objects)");
  }

  static void allocation(String name, Runnable work) {
    for (var i = 0; i < 200_000; i++) {
      work.run();
    }
    var before = Demo.allocatedBytes();
    for (var i = 0; i < N; i++) {
      work.run();
    }
    System.out.printf("  %-36s %5.1f bytes/op%n", name, (double) (Demo.allocatedBytes() - before) / N);
  }
}
