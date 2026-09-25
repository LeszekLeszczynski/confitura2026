package com.decerto.leszek.confitura2026.demo05_speed;

import com.decerto.leszek.confitura2026.Demo;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Summing one field over a million elements: flat value array vs. the same value class behind
 * references vs. a classic identity record. "scattered" fills the reference arrays in shuffled order
 * so the objects are not neatly adjacent in memory - what a heap looks like after a few GC cycles.
 * At n=1M everything fits in cache; 16M elements is where memory layout starts to matter.
 *
 * <pre>
 * ./run.sh -p FlatArraySpeed
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FlatArraySpeed {

  value record Small(int a, short b, byte c) {}

  record SmallIdentity(int a, short b, byte c) {}

  @Param({"1000000", "16000000"})
  int n;

  @Param({"false", "true"})
  boolean scattered;

  Small[] flat;
  Small[] refs;
  SmallIdentity[] identity;

  @Setup
  public void setup() {
    flat = new Small[n];
    refs = Demo.newReferenceArray(Small.class, n);
    identity = new SmallIdentity[n];

    var order = IntStream.range(0, n).boxed().collect(Collectors.toList());
    if (scattered) {
      Collections.shuffle(order, new Random(42));
    }
    order.stream()
        .mapToInt(Integer::intValue)
        .forEach(
            i -> {
              flat[i] = new Small(i, (short) i, (byte) i);
              refs[i] = new Small(i, (short) i, (byte) i);
              identity[i] = new SmallIdentity(i, (short) i, (byte) i);
            });

    System.out.printf(
        "%n# flat=%s refs=%s identity=%s%n",
        Demo.isFlatArray(flat), Demo.isFlatArray(refs), Demo.isFlatArray(identity));
  }

  @Benchmark
  public long sumFlat() {
    var sum = 0L;
    for (var e : flat) {
      sum += e.a();
    }
    return sum;
  }

  @Benchmark
  public long sumRefs() {
    var sum = 0L;
    for (var e : refs) {
      sum += e.a();
    }
    return sum;
  }

  @Benchmark
  public long sumIdentity() {
    var sum = 0L;
    for (var e : identity) {
      sum += e.a();
    }
    return sum;
  }

  public static void main(String[] args) throws RunnerException {
    new Runner(new OptionsBuilder().include(FlatArraySpeed.class.getSimpleName()).build()).run();
  }
}
