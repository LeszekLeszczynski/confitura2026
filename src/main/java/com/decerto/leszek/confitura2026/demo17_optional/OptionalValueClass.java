package com.decerto.leszek.confitura2026.demo17_optional;

import com.decerto.leszek.confitura2026.Demo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Optional was always "a value-based class"; with preview it is a value class. That changes three
 * things you can measure: how it sits in a field or an array, whether returning one from a method
 * allocates, and what == means. Same code, run plain and with preview.
 *
 * <pre>
 * ./run.sh    OptionalValueClass
 * ./run.sh -p OptionalValueClass
 * </pre>
 */
public class OptionalValueClass {

  static final int N = 1_000_000;
  static final String[] NAMES = {"ada", "bob", "cyd", "dee", "eve", "fay", "gus", "hal"};

  static class Customer {
    Optional<String> nickname = Optional.empty();
    Optional<String> referrer = Optional.of("ada");
  }

  /** The typical repository method: found or not. Half of the calls find something. */
  static Optional<String> find(int id) {
    return id % 2 == 0 ? Optional.of(NAMES[id & 7]) : Optional.empty();
  }

  static Customer customer = new Customer();
  static int sink;

  public static void main(String[] args) {
    System.out.println("Optional is a value class: " + Optional.class.isValue());

    Demo.section("layout");
    Demo.shape(new Customer());
    var array = new Optional[N];
    System.out.printf("  new Optional[%,d]: flat=%s, %,d bytes%n", N, Demo.isFlatArray(array), Demo.sizeOf(array));

    Demo.section("bytes allocated per call, after JIT warm-up");
    measure("find(i).orElse(\"none\")            (consumed at once)", i -> find(i).orElse("none").length());
    measure("customer.nickname = find(i)       (stored in a field)", i -> {
      customer.nickname = find(i);
      return 0;
    });
    measure("find(i).map(String::length)       (chained)", i -> find(i).map(String::length).orElse(0));
    var list = new ArrayList<Optional<String>>(N + 200_000); // warm-up adds too; no growth during measurement
    measure("list.add(find(i))                 (stored in a List)", i -> {
      list.add(find(i));
      return 0;
    });

    Demo.section("identity");
    var a = Optional.of("ada");
    var b = Optional.of("ada");
    System.out.println("  Optional.of(\"ada\") == Optional.of(\"ada\"): " + (a == b));
    System.out.println("  Optional.empty() == Optional.empty():     " + (Optional.empty() == Optional.empty()));
  }

  static void measure(String name, IntFunction<Integer> work) {
    for (var i = 0; i < 200_000; i++) {
      sink += work.apply(i); // warm-up: let C2 compile the loop and inline find()
    }
    var before = Demo.allocatedBytes();
    for (var i = 0; i < N; i++) {
      sink += work.apply(i);
    }
    var perCall = (double) (Demo.allocatedBytes() - before) / N;
    System.out.printf("  %-56s %5.1f bytes/call%n", name, perCall);
  }
}
