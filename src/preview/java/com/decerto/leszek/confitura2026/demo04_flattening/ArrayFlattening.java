package com.decerto.leszek.confitura2026.demo04_flattening;

import com.decerto.leszek.confitura2026.Demo;
import java.util.function.IntFunction;

/**
 * When does the JVM flatten an array of value objects? Only when one element - payload plus a null
 * marker - fits in a single atomic 64-bit access. Small (7 bytes) qualifies, Big (64 bytes) does
 * not and falls back to an array of references to heap objects.
 *
 * <pre>
 * ./run.sh -p ArrayFlattening
 * ./run.sh -p ArrayFlattening -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlatArrayLayout
 * </pre>
 */
public class ArrayFlattening {

  static final int N = 1_000_000;

  /** 4 + 2 + 1 = 7 bytes of payload. */
  value record Small(int a, short b, byte c) {}

  /** 8 * 8 = 64 bytes of payload. */
  value record Big(long a, long b, long c, long d, long e, long f, long g, long h) {}

  public static void main(String[] args) {
    Demo.vm();
    measure("Small (7 B)", Small[]::new, i -> new Small(i, (short) i, (byte) i));
    measure("Big (64 B)", Big[]::new, i -> new Big(i, i, i, i, i, i, i, i));
  }

  static <T> void measure(String name, IntFunction<T[]> newArray, IntFunction<T> newElement) {
    Demo.section(name);
    var before = Demo.allocatedBytes();
    var array = newArray.apply(N);
    for (var i = 0; i < N; i++) {
      array[i] = newElement.apply(i);
    }
    var allocated = Demo.allocatedBytes() - before;

    System.out.printf("flat array:        %s%n", Demo.isFlatArray(array));
    System.out.printf("allocated:         %,14d bytes%n", allocated);
    System.out.printf("per element:       %,14d bytes%n", allocated / N);
    System.out.printf("last element:      %s%n", array[N - 1]);
  }
}
