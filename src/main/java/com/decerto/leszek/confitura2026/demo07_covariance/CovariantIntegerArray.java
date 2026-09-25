package com.decerto.leszek.confitura2026.demo07_covariance;

import com.decerto.leszek.confitura2026.Demo;

/**
 * Array covariance meets value classes. With preview enabled Integer is a value class, so new
 * Integer[N] is a flat array - and it stays flat when viewed through Comparable[] or Object[].
 * Covariance still behaves (ArrayStoreException, nulls), but some old truths quietly change.
 *
 * <pre>
 * ./run.sh    CovariantIntegerArray
 * ./run.sh -p CovariantIntegerArray
 * </pre>
 */
public class CovariantIntegerArray {

  static final int N = 1_000_000;

  public static void main(String[] args) {
    System.out.println("Integer is a value class: " + Integer.class.isValue());

    Demo.section("Comparable[] numbers = new Integer[N]");
    var before = Demo.allocatedBytes();
    Comparable[] numbers = new Integer[N];
    for (var i = 0; i < N; i++) {
      numbers[i] = i; // autoboxing: Integer.valueOf(i)
    }
    var allocated = Demo.allocatedBytes() - before;
    System.out.printf("flat array:   %s%n", Demo.isFlatArray(numbers));
    System.out.printf("allocated:    %,14d bytes%n", allocated);
    System.out.printf("per element:  %,14d bytes%n", allocated / N);

    Demo.section("covariance still works");
    try {
      numbers[0] = "boom";
    } catch (ArrayStoreException e) {
      System.out.println("numbers[0] = \"boom\"  -> ArrayStoreException");
    }
    numbers[1] = null;
    System.out.println("numbers[1] = null    -> ok, numbers[1] = " + numbers[1]);

    Demo.section("other wrappers");
    System.out.printf("Long[]   flat: %s  (8 B payload + null marker > 64 bits)%n", Demo.isFlatArray(new Long[N]));
    System.out.printf("Short[]  flat: %s%n", Demo.isFlatArray(new Short[N]));

    Demo.section("the old interview question");
    Integer a = 1000;
    Integer b = 1000;
    System.out.println("Integer a = 1000, b = 1000; a == b  ->  " + (a == b));
  }
}
