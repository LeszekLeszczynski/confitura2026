package com.decerto.leszek.confitura2026.demo24_practice;

import com.decerto.leszek.confitura2026.Demo;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.LongSupplier;

/**
 * The benefit that needs no change to your code: java.time is made of small immutable values, so
 * every intermediate step of a chain used to be a heap object. Escape analysis already removed some
 * of them; with preview there is nothing left to remove. Run plain, then with preview.
 *
 * <pre>
 * ./run.sh    DateChain
 * ./run.sh -p DateChain
 * </pre>
 */
public class DateChain {

  static final int N = 10_000;
  static final int WARMUP_ROUNDS = 500;
  static final int ROUNDS = 100;
  static final LocalDate START = LocalDate.of(2026, 9, 25);
  static long sink;

  public static void main(String[] args) {
    System.out.println("LocalDate is a value class: " + LocalDate.class.isValue()
        + ", Duration: " + Duration.class.isValue());

    Demo.section("bytes allocated per chain, fully warmed up");
    measure("date.plusDays(i).withDayOfMonth(1).plusMonths(3)  (2 intermediates + result)",
        i -> START.plusDays(i % 28).withDayOfMonth(1).plusMonths(3).getDayOfYear());
    measure("date.plusDays(i)                                  (result only)",
        i -> START.plusDays(i % 28).getDayOfYear());
    measure("Duration.ofMinutes(i).plusSeconds(30).toSeconds() (1 intermediate)",
        i -> Duration.ofMinutes(i % 60).plusSeconds(30).toSeconds());
  }

  interface Work {
    long apply(int i);
  }

  static void measure(String name, Work work) {
    for (var round = 0; round < WARMUP_ROUNDS; round++) {
      sink += loop(work);
    }
    var before = Demo.allocatedBytes();
    for (var round = 0; round < ROUNDS; round++) {
      sink += loop(work);
    }
    System.out.printf("  %-68s %6.2f bytes%n", name, (double) (Demo.allocatedBytes() - before) / ROUNDS / N);
  }

  static long loop(Work work) {
    var total = 0L;
    for (var i = 0; i < N; i++) {
      total += work.apply(i);
    }
    return total;
  }
}
