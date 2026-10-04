package com.decerto.leszek.confitura2026.demo26_money;

import com.decerto.leszek.confitura2026.demo26_money.MoneyArithmetic.BigMoney;
import com.decerto.leszek.confitura2026.demo26_money.MoneyArithmetic.LongMoney;
import com.decerto.leszek.confitura2026.demo26_money.ValueMoneyArithmetic.PlainMoney;
import com.decerto.leszek.confitura2026.demo26_money.ValueMoneyArithmetic.UncheckedMoney;
import com.decerto.leszek.confitura2026.demo26_money.ValueMoneyArithmetic.ValueBigMoney;
import com.decerto.leszek.confitura2026.demo26_money.ValueMoneyArithmetic.ValueMoney;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * What does a domain type for money actually cost? The same premium calculation - base, loading,
 * tax, summed over n risks - in five representations. All five must agree to the cent, which is
 * checked in setup before anything is measured.
 *
 * <pre>
 * ./run.sh -p PremiumCalculatorBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class PremiumCalculatorBenchmark {

  @Param({"1000"})
  int n;

  long[] baseCents;
  int[] loadings;
  BigMoney[] bigBases;
  LongMoney[] longBases;
  ValueMoney[] valueBases;
  ValueBigMoney[] valueBigBases;
  PlainMoney[] plainBases;
  UncheckedMoney[] uncheckedBases;

  @Setup
  public void setup() {
    baseCents = new long[n];
    loadings = new int[n];
    bigBases = new BigMoney[n];
    longBases = new LongMoney[n];
    valueBases = new ValueMoney[n];
    valueBigBases = new ValueBigMoney[n];
    plainBases = new PlainMoney[n];
    uncheckedBases = new UncheckedMoney[n];
    for (var i = 0; i < n; i++) {
      baseCents[i] = 100_000 + i * 37L;
      loadings[i] = 100 + i % 73;
      bigBases[i] = new BigMoney(BigDecimal.valueOf(baseCents[i], 2));
      longBases[i] = new LongMoney(baseCents[i]);
      valueBases[i] = new ValueMoney(baseCents[i]);
      valueBigBases[i] = new ValueBigMoney(BigDecimal.valueOf(baseCents[i], 2));
      plainBases[i] = new PlainMoney(baseCents[i]);
      uncheckedBases[i] = new UncheckedMoney(baseCents[i]);
    }

    // all five must compute the same premium, or the comparison is meaningless
    var raw = MoneyArithmetic.premiumRaw(baseCents, loadings);
    var big = MoneyArithmetic.premiumBigDecimal(bigBases, loadings);
    var money = MoneyArithmetic.premiumLongMoney(longBases, loadings);
    var value = ValueMoneyArithmetic.premiumValueMoney(valueBases, loadings);
    var valueBig = ValueMoneyArithmetic.premiumValueBigMoney(valueBigBases, loadings);
    if (raw != big || raw != money || raw != value || raw != valueBig) {
      throw new AssertionError("variants disagree: raw=%d big=%d money=%d value=%d valueBig=%d"
          .formatted(raw, big, money, value, valueBig));
    }
    System.out.printf("%n# all five variants agree: %,d cents for %,d risks%n", raw, n);
  }

  @Benchmark
  public long rawLongs() {
    return MoneyArithmetic.premiumRaw(baseCents, loadings);
  }

  @Benchmark
  public long moneyOverBigDecimal() {
    return MoneyArithmetic.premiumBigDecimal(bigBases, loadings);
  }

  @Benchmark
  public long moneyOverLong() {
    return MoneyArithmetic.premiumLongMoney(longBases, loadings);
  }

  @Benchmark
  public long valueMoneyOverLong() {
    return ValueMoneyArithmetic.premiumValueMoney(valueBases, loadings);
  }

  @Benchmark
  public long valueMoneyOverBigDecimal() {
    return ValueMoneyArithmetic.premiumValueBigMoney(valueBigBases, loadings);
  }

  /** Diagnostic only: the exact calculation of demo 24 (2 operations, no rounding, no validation). */
  @Benchmark
  public long diagnosticTwoOpsNoValidation() {
    return ValueMoneyArithmetic.premiumPlainMoney(plainBases, loadings);
  }

  /** Diagnostic only: the real calculation without the constructor's invariant check. */
  @Benchmark
  public long diagnosticNoInvariantCheck() {
    return ValueMoneyArithmetic.premiumUncheckedMoney(uncheckedBases, loadings);
  }

  public static void main(String[] args) throws RunnerException {
    new Runner(new OptionsBuilder()
        .include(PremiumCalculatorBenchmark.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .build()).run();
  }
}
