package com.decerto.leszek.confitura2026.demo06_lists;

import com.decerto.leszek.confitura2026.Demo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The trap: a flat array is great, but every collection in java.util stores its elements in an
 * Object[]. Put value objects into a List and each one is "buffered" - materialized as a real heap
 * object with a header - and you are back to the reference layout. Only Arrays.asList keeps the
 * original (flat) array underneath.
 *
 * <pre>
 * ./run.sh -p ValueObjectsInList
 * </pre>
 */
public class ValueObjectsInList {

  static final int N = 1_000_000;

  value record Small(int a, short b, byte c) {}

  public static void main(String[] args) {
    var array = measure("Small[]", () -> fillArray(new Small[N]));
    System.out.println("flat array: " + Demo.isFlatArray(array));

    measure("new ArrayList<>(N) + add", () -> fillList(new ArrayList<>(N)));
    measure("new ArrayList<>() + add (growing)", () -> fillList(new ArrayList<>()));
    measure("Arrays.asList(array)", () -> Arrays.asList(array));
    measure("List.of(array)", () -> List.of(array));
    measure("Arrays.stream(array).toList()", () -> Arrays.stream(array).toList());
    measure("new ArrayList<>(Arrays.asList(array))", () -> new ArrayList<>(Arrays.asList(array)));
  }

  static <T> T measure(String name, Supplier<T> build) {
    Demo.section(name);
    var before = Demo.allocatedBytes();
    var result = build.get();
    var allocated = Demo.allocatedBytes() - before;
    System.out.printf("allocated:    %,14d bytes%n", allocated);
    System.out.printf("per element:  %,14d bytes%n", allocated / N);
    return result;
  }

  static Small[] fillArray(Small[] array) {
    IntStream.range(0, N).forEach(i -> array[i] = new Small(i, (short) i, (byte) i));
    return array;
  }

  static List<Small> fillList(List<Small> list) {
    IntStream.range(0, N).forEach(i -> list.add(new Small(i, (short) i, (byte) i)));
    return list;
  }
}
