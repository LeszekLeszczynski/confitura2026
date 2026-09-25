package com.decerto.leszek.confitura2026.demo23_scalarization;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Flattening is about data on the heap; scalarization is about code. A value object has no identity,
 * so the JIT may keep its fields in registers and never build the object at all - and unlike escape
 * analysis for ordinary objects, that survives a method call, because the compiled method takes and
 * returns the fields rather than a pointer.
 *
 * <p>The loop is the same in every row: s = s.plus(new Point(xs[i], ys[i])), 10,000 times. The
 * second column runs in a forked JVM with -XX:CompileCommand=dontinline, so escape analysis has no
 * chance: the object would have to exist to be passed.
 *
 * <pre>
 * ./run.sh -p Scalarization
 * </pre>
 */
public class Scalarization {

  static final int N = 10_000;
  static final int ROUNDS = 100;
  static final int[] XS = new int[N];
  static final int[] YS = new int[N];

  static {
    for (var i = 0; i < N; i++) {
      XS[i] = i;
      YS[i] = -i;
    }
  }

  /** 8 bytes of payload: too big to flatten in a plain field (demo 22), irrelevant here. */
  value record Point(int x, int y) {
    Point plus(Point o) {
      return new Point(x + o.x, y + o.y);
    }
  }

  /** The identity twin: the JVM must prove the object does not escape, and it cannot. */
  record IdentityPoint(int x, int y) {
    IdentityPoint plus(IdentityPoint o) {
      return new IdentityPoint(x + o.x, y + o.y);
    }
  }

  /** 32 bytes of payload - far over any flattening limit. The stack has no tearing problem. */
  value record Big(long a, long b, long c, long d) {
    Big plus(Big o) {
      return new Big(a + o.a, b + o.b, c + o.c, d + o.d);
    }
  }

  /** Erased signature: no concrete value type to scalarize, so the object has to be built. */
  static Object plusViaObject(Object left, Object right) {
    var a = (Point) left;
    var b = (Point) right;
    return new Point(a.x() + b.x(), a.y() + b.y());
  }

  static long sink;

  static long sumValue() {
    var s = new Point(0, 0);
    for (var i = 0; i < N; i++) {
      s = s.plus(new Point(XS[i], YS[i]));
    }
    return s.x();
  }

  static long sumIdentity() {
    var s = new IdentityPoint(0, 0);
    for (var i = 0; i < N; i++) {
      s = s.plus(new IdentityPoint(XS[i], YS[i]));
    }
    return s.x();
  }

  static long sumBig() {
    var s = new Big(0, 0, 0, 0);
    for (var i = 0; i < N; i++) {
      s = s.plus(new Big(XS[i], YS[i], XS[i], YS[i]));
    }
    return s.a();
  }

  static long sumViaObject() {
    Object s = new Point(0, 0);
    for (var i = 0; i < N; i++) {
      s = plusViaObject(s, new Point(XS[i], YS[i]));
    }
    return ((Point) s).x();
  }

  static final Map<String, LongSupplier> ROWS = new LinkedHashMap<>();

  static {
    ROWS.put("identity record Point (8 B)", Scalarization::sumIdentity);
    ROWS.put("value record Point (8 B)", Scalarization::sumValue);
    ROWS.put("value record Big (32 B)", Scalarization::sumBig);
    ROWS.put("value Point via Object parameter", Scalarization::sumViaObject);
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("--child")) {
      ROWS.forEach((name, work) -> System.out.printf("%s|%.2f%n", name, measure(work)));
      return;
    }

    Demo.section("bytes allocated per plus(), after warm-up (" + N + " calls x " + ROUNDS + " rounds)");
    var inlined = new LinkedHashMap<String, Double>();
    ROWS.forEach((name, work) -> inlined.put(name, measure(work)));
    var dontinline = child();

    System.out.printf("  %-34s %16s %16s%n", "", "inlining allowed", "dontinline");
    ROWS.keySet().forEach(name ->
        System.out.printf("  %-34s %14.2f B %14s B%n", name, inlined.get(name), dontinline.getOrDefault(name, "?")));
    System.out.println();
    System.out.println("  inlining allowed: escape analysis may remove the allocation - for identity objects too");
    System.out.println("  dontinline:       the object would have to exist to be passed, so only scalarization helps");
  }

  /** Bytes allocated per plus() call: warm up, then measure whole rounds of the loop. */
  static double measure(LongSupplier work) {
    for (var round = 0; round < ROUNDS; round++) {
      sink += work.getAsLong();
    }
    var before = Demo.allocatedBytes();
    for (var round = 0; round < ROUNDS; round++) {
      sink += work.getAsLong();
    }
    return (double) (Demo.allocatedBytes() - before) / ROUNDS / N;
  }

  /** The same measurements in a JVM where plus() may not be inlined. */
  static Map<String, String> child() throws Exception {
    var command = new ArrayList<String>();
    command.add(ProcessHandle.current().info().command().orElseThrow());
    command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
    command.addAll(List.of(
        "-XX:CompileCommand=dontinline,*::plus",
        "-XX:CompileCommand=dontinline,*::plusViaObject",
        "-cp", System.getProperty("java.class.path"),
        Scalarization.class.getName(), "--child"));
    var process = new ProcessBuilder(command).redirectErrorStream(true).start();
    var results = new LinkedHashMap<String, String>();
    try (var lines = new String(process.getInputStream().readAllBytes()).lines()) {
      lines.filter(line -> line.contains("|"))
          .forEach(line -> results.put(line.substring(0, line.indexOf('|')), line.substring(line.indexOf('|') + 1)));
    }
    process.waitFor();
    return results;
  }
}
