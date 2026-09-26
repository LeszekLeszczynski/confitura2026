package com.decerto.leszek.confitura2026.demo24_practice;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

/**
 * Three things scalarization makes practical - returning two values, doing arithmetic on domain
 * types in a hot loop, and chaining java.time calls - and one thing it does not: a stream, whose
 * BinaryOperator is generic and therefore erased to Object.
 *
 * <p>The second column is measured in a forked JVM with -XX:CompileCommand=dontinline on the
 * operations, which is what a long, cold, or megamorphic call chain looks like to the JIT.
 *
 * <pre>
 * ./run.sh -p ScalarizationInPractice
 * </pre>
 */
public class ScalarizationInPractice {

  static final int SIZE = 10_000;
  static final int WARMUP_ROUNDS = 500;
  static final int ROUNDS = 100;
  static final int[] DATA = IntStream.range(0, 8).map(i -> (i * 7919) % 10_000).toArray();

  // ---- 1. returning two values -----------------------------------------------------------------

  value record MinMax(int min, int max) {}

  record IdentityMinMax(int min, int max) {}

  static MinMax minMax(int[] data) {
    var min = Integer.MAX_VALUE;
    var max = Integer.MIN_VALUE;
    for (var value : data) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    return new MinMax(min, max);
  }

  static IdentityMinMax minMaxIdentity(int[] data) {
    var min = Integer.MAX_VALUE;
    var max = Integer.MIN_VALUE;
    for (var value : data) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    return new IdentityMinMax(min, max);
  }

  /** The old trick: two ints packed into a long, because a tuple object was too expensive. */
  static long minMaxPacked(int[] data) {
    var min = Integer.MAX_VALUE;
    var max = Integer.MIN_VALUE;
    for (var value : data) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    return ((long) min << 32) | (max & 0xffff_ffffL);
  }

  // ---- 2. domain arithmetic in a hot loop -------------------------------------------------------

  static value record Money(long cents) {
    static final Money ZERO = new Money(0);

    Money plus(Money other) {
      return new Money(cents + other.cents);
    }

    Money times(int percent) {
      return new Money(cents * percent / 100);
    }
  }

  static record IdentityMoney(long cents) {
    static final IdentityMoney ZERO = new IdentityMoney(0);

    IdentityMoney plus(IdentityMoney other) {
      return new IdentityMoney(cents + other.cents);
    }

    IdentityMoney times(int percent) {
      return new IdentityMoney(cents * percent / 100);
    }
  }

  static record Risk(Money base, int loading) {}

  static record IdentityRisk(IdentityMoney base, int loading) {}

  static final List<Risk> RISKS = IntStream.range(0, SIZE)
      .mapToObj(i -> new Risk(new Money(1000 + i), 100 + i % 50))
      .toList();
  static final List<IdentityRisk> IDENTITY_RISKS = IntStream.range(0, SIZE)
      .mapToObj(i -> new IdentityRisk(new IdentityMoney(1000 + i), 100 + i % 50))
      .toList();
  static final List<Money> AMOUNTS = RISKS.stream().map(Risk::base).toList();

  static long priceValue() {
    var total = Money.ZERO;
    for (var risk : RISKS) {
      total = total.plus(risk.base().times(risk.loading()));
    }
    return total.cents();
  }

  static long priceIdentity() {
    var total = IdentityMoney.ZERO;
    for (var risk : IDENTITY_RISKS) {
      total = total.plus(risk.base().times(risk.loading()));
    }
    return total.cents();
  }

  static long priceLong() {
    var total = 0L;
    for (var risk : RISKS) {
      total += risk.base().cents() * risk.loading() / 100;
    }
    return total;
  }

  // ---- 3. loop vs stream -------------------------------------------------------------------------

  static long sumLoop() {
    var total = Money.ZERO;
    for (var money : AMOUNTS) {
      total = total.plus(money);
    }
    return total.cents();
  }

  static long sumStream() {
    return AMOUNTS.stream().reduce(Money.ZERO, Money::plus).cents();
  }

  static long sumMapReduce() {
    return RISKS.stream().map(risk -> risk.base().times(risk.loading())).reduce(Money.ZERO, Money::plus).cents();
  }

  static long sumMapToLong() {
    return RISKS.stream().mapToLong(risk -> risk.base().cents() * risk.loading() / 100).sum();
  }

  static long callMinMax() {
    var total = 0L;
    for (var i = 0; i < SIZE; i++) {
      total += minMax(DATA).min();
    }
    return total;
  }

  static long callMinMaxIdentity() {
    var total = 0L;
    for (var i = 0; i < SIZE; i++) {
      total += minMaxIdentity(DATA).min();
    }
    return total;
  }

  static long callMinMaxPacked() {
    var total = 0L;
    for (var i = 0; i < SIZE; i++) {
      total += minMaxPacked(DATA) >> 32;
    }
    return total;
  }

  static long sink;

  static final Map<String, LongSupplier> ROWS = new LinkedHashMap<>();

  static {
    ROWS.put("1. return MinMax (value record)", ScalarizationInPractice::callMinMax);
    ROWS.put("1. return MinMax (identity record)", ScalarizationInPractice::callMinMaxIdentity);
    ROWS.put("1. return two ints packed in a long", ScalarizationInPractice::callMinMaxPacked);
    ROWS.put("2. pricing loop, value Money", ScalarizationInPractice::priceValue);
    ROWS.put("2. pricing loop, identity Money", ScalarizationInPractice::priceIdentity);
    ROWS.put("2. pricing loop, raw long", ScalarizationInPractice::priceLong);
    ROWS.put("3. for loop, total.plus(m)", ScalarizationInPractice::sumLoop);
    ROWS.put("3. stream().reduce(ZERO, Money::plus)", ScalarizationInPractice::sumStream);
    ROWS.put("3. stream().map(...).reduce(...)", ScalarizationInPractice::sumMapReduce);
    ROWS.put("3. stream().mapToLong(...).sum()", ScalarizationInPractice::sumMapToLong);
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("--child")) {
      ROWS.forEach((name, work) -> System.out.printf("%s|%.2f%n", name, measure(work)));
      return;
    }

    Demo.section("bytes allocated per element, after warm-up (" + SIZE + " elements x " + ROUNDS + " rounds)");
    var inlined = new LinkedHashMap<String, Double>();
    ROWS.forEach((name, work) -> inlined.put(name, measure(work)));
    var dontinline = child();
    System.out.printf("  %-40s %16s %14s%n", "", "warmed up (C2)", "C1 only");
    ROWS.keySet().forEach(name ->
        System.out.printf("  %-40s %14.2f B %12s B%n", name, inlined.get(name), dontinline.getOrDefault(name, "?")));

    Demo.section("and the JDK's own value classes, no code change needed");
    DateChain.main(new String[0]);
  }

  static double measure(LongSupplier work) {
    for (var round = 0; round < WARMUP_ROUNDS; round++) {
      sink += work.getAsLong(); // scalarization only happens once C2 has compiled the method
    }
    var before = Demo.allocatedBytes();
    for (var round = 0; round < ROUNDS; round++) {
      sink += work.getAsLong();
    }
    return (double) (Demo.allocatedBytes() - before) / ROUNDS / SIZE;
  }

  static Map<String, String> child() throws Exception {
    var command = new ArrayList<String>();
    command.add(ProcessHandle.current().info().command().orElseThrow());
    command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
    command.addAll(List.of(
        "-XX:TieredStopAtLevel=1", // C1 only: what the code looks like before C2 gets to it
        "-cp", System.getProperty("java.class.path"),
        ScalarizationInPractice.class.getName(), "--child"));
    var process = new ProcessBuilder(command).redirectErrorStream(true).start();
    var results = new LinkedHashMap<String, String>();
    new String(process.getInputStream().readAllBytes()).lines()
        .filter(line -> line.contains("|"))
        .forEach(line -> results.put(line.substring(0, line.indexOf('|')), line.substring(line.indexOf('|') + 1)));
    process.waitFor();
    return results;
  }
}
