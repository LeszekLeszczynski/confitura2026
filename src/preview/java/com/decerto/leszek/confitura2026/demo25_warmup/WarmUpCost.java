package com.decerto.leszek.confitura2026.demo25_warmup;

import com.decerto.leszek.confitura2026.Demo;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Reading a flattened field hands out a value that does not exist as an object, so the JVM has to
 * build one - until C2 compiles the method and stops building it. Nothing here changes between
 * batches except how often the loop has run.
 *
 * <pre>
 * ./run.sh -p WarmUpCost
 * </pre>
 */
public class WarmUpCost {

  static final int SIZE = 10_000;
  static final int CALLS_PER_BATCH = 20;
  static final int BATCHES = 12;

  value record Money(long cents) {}

  /** Money is a record component, so it sits inline: no Money object exists on the heap. */
  record Risk(Money base, int loading) {}

  static final List<Risk> RISKS =
      IntStream.range(0, SIZE).mapToObj(i -> new Risk(new Money(1000 + i), 100)).toList();

  static long sink;

  /** One pass: read the flattened Money out of every Risk and add up the cents. */
  static long readAll() {
    var total = 0L;
    for (var risk : RISKS) {
      total += risk.base().cents();
    }
    return total;
  }

  public static void main(String[] args) {
    System.out.printf("Money inline in Risk: %s, Risk on the heap: %d B, buffered Money would be: %d B%n",
        Demo.isFlatField(Risk.class, "base"), Demo.sizeOf(RISKS.get(0)), Demo.sizeOf(RISKS.get(0).base()));

    Demo.section("the same loop over " + String.format("%,d", SIZE) + " risks, batch after batch");
    var warmUpWaste = 0L;
    for (var batch = 1; batch <= BATCHES; batch++) {
      var before = Demo.allocatedBytes();
      for (var call = 0; call < CALLS_PER_BATCH; call++) {
        sink += readAll();
      }
      var allocated = Demo.allocatedBytes() - before;
      var perRead = (double) allocated / CALLS_PER_BATCH / SIZE;
      warmUpWaste += perRead < 1 ? 0 : allocated;
      System.out.printf("  batch %2d  (calls %3d-%3d)  %6.2f B per read  %,12d B total%s%n",
          batch, (batch - 1) * CALLS_PER_BATCH + 1, batch * CALLS_PER_BATCH,
          perRead, allocated, perRead < 1 ? "   <- C2 has compiled it: no Money is built any more" : "");
    }

    System.out.printf("%n  the whole warm-up cost once, per JVM: %,d B of young-gen garbage%n", warmUpWaste);
    System.out.printf("  what it buys, for as long as the data lives: %,d B and %,d objects saved%n",
        SIZE * 16, SIZE);
    System.out.println("  (an identity Money would be a separate 16 B object per Risk - and free to read, always)");
  }
}
