package com.decerto.leszek.confitura2026.demo03_epsilon;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Same million dates as ObjectTable, but instead of asking JOL we ask the JVM. With
 * -XX:+UseEpsilonGC nothing is ever freed, so "heap used" after the loop is exactly what the loop
 * allocated - including any temporaries.
 *
 * <pre>
 * ./run.sh    EpsilonAllocation -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 * ./run.sh -p EpsilonAllocation -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 * </pre>
 */
public class EpsilonAllocation {

  static final int N = 1_000_000;

  public static void main(String[] args) {
    System.out.println("GC: " + gcNames());

    var thread = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    var heapBefore = heapUsed();
    var allocBefore = thread.getCurrentThreadAllocatedBytes();

    var dates = new LocalDate[N];
    IntStream.range(0, N)
        .forEach(i -> dates[i] = LocalDate.of(2000 + i % 27, 1 + i % 12, 1 + i % 28));

    var allocAfter = thread.getCurrentThreadAllocatedBytes();
    var heapAfter = heapUsed();

    System.out.printf("%,d dates, last one %s%n", dates.length, dates[N - 1]);
    System.out.printf("heap used delta:        %,12d bytes%n", heapAfter - heapBefore);
    System.out.printf("thread allocated delta: %,12d bytes%n", allocAfter - allocBefore);
    System.out.printf("per element:            %,12d bytes%n", (allocAfter - allocBefore) / N);
  }

  static long heapUsed() {
    var runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  static String gcNames() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .map(gc -> gc.getName())
        .collect(Collectors.joining(", "));
  }
}
