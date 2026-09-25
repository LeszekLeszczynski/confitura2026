package com.decerto.leszek.confitura2026.demo09_identity;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Value objects have no identity, so everything that needs one stops working. None of these
 * classes are ours - Integer, Optional and LocalDate simply became value classes. Run without
 * preview to see the code every existing codebase relies on, then with preview to see it throw.
 *
 * <pre>
 * ./run.sh    IdentityOperations
 * ./run.sh -p IdentityOperations
 * </pre>
 */
public class IdentityOperations {

  static final Cleaner CLEANER = Cleaner.create();

  public static void main(String[] args) {
    Stream.of(Integer.class, Optional.class, LocalDate.class)
        .forEach(c -> System.out.printf("%-10s value class: %s%n", c.getSimpleName(), c.isValue()));

    check("Integer", Integer.valueOf(1000), Integer.valueOf(1000));
    check("Optional", Optional.of("x"), Optional.of("x"));
    check("LocalDate", LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 18));
  }

  /** Two separately constructed, equal objects. */
  static void check(String name, Object a, Object b) {
    Demo.section(name);
    attempt("synchronized (a) {}", () -> {
      synchronized (a) {
        return "ok";
      }
    });
    attempt("new WeakReference<>(a)", () -> new WeakReference<>(a).get() == a ? "ok" : "lost");
    attempt("Cleaner.register(a, ...)", () -> {
      CLEANER.register(a, () -> {});
      return "ok";
    });
    attempt("a == b", () -> String.valueOf(a == b));
    attempt("identityHashCode(a) == identityHashCode(b)",
        () -> String.valueOf(System.identityHashCode(a) == System.identityHashCode(b)));
    attempt("new IdentityHashMap(){a -> 1}.get(b)", () -> {
      var map = new IdentityHashMap<Object, Integer>();
      map.put(a, 1);
      return String.valueOf(map.get(b));
    });
  }

  interface Operation {
    String run() throws Exception;
  }

  static void attempt(String what, Operation operation) {
    String outcome;
    try {
      outcome = operation.run();
    } catch (Exception e) {
      outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    System.out.printf("  %-44s -> %s%n", what, outcome);
  }
}
