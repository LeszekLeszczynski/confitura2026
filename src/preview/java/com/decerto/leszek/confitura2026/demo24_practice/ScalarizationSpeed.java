package com.decerto.leszek.confitura2026.demo24_practice;

import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationInPractice.IdentityMoney;
import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationInPractice.IdentityRisk;
import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationInPractice.Money;
import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationInPractice.Risk;
import java.util.ArrayList;
import java.util.List;
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
 * Demo 24 shows that the value version stops allocating. This asks the other question: is it also
 * faster? One loop, three ways to spell the same pricing calculation - a value class, the identity
 * class it replaces, and no domain type at all. gc.alloc.rate.norm is bytes per operation, so time
 * and allocation are visible side by side.
 *
 * <pre>
 * ./run.sh -p ScalarizationSpeed      (about a minute)
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ScalarizationSpeed {

  @Param({"10000"})
  int n;

  List<Risk> risks;
  List<IdentityRisk> identityRisks;

  @Setup
  public void setup() {
    risks = new ArrayList<>(n);
    identityRisks = new ArrayList<>(n);
    for (var i = 0; i < n; i++) {
      risks.add(new Risk(new Money(1000 + i), 100 + i % 50));
      identityRisks.add(new IdentityRisk(new IdentityMoney(1000 + i), 100 + i % 50));
    }
  }

  /** The domain model we want: Money in, Money out. */
  @Benchmark
  public long pricingValue() {
    var total = Money.ZERO;
    for (var risk : risks) {
      total = total.plus(risk.base().times(risk.loading()));
    }
    return total.cents();
  }

  /** The same model before JEP 401: an identity record. */
  @Benchmark
  public long pricingIdentity() {
    var total = IdentityMoney.ZERO;
    for (var risk : identityRisks) {
      total = total.plus(risk.base().times(risk.loading()));
    }
    return total.cents();
  }

  /** What people write when the domain type "costs too much": bare longs. */
  @Benchmark
  public long pricingRawLong() {
    var total = 0L;
    for (var risk : risks) {
      total += risk.base().cents() * risk.loading() / 100;
    }
    return total;
  }

  public static void main(String[] args) throws RunnerException {
    new Runner(new OptionsBuilder()
        .include(ScalarizationSpeed.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .build()).run();
  }
}
