package com.decerto.leszek.confitura2026.demo13_identitymap;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Two textbook uses of IdentityHashMap - a serializer that writes back-references instead of
 * repeating an object it has already written, and a footprint estimator that counts distinct
 * reachable objects - fed an object graph whose leaves are Integer and LocalDate. Same code, same
 * graph, run plain and with preview.
 *
 * <pre>
 * ./run.sh    IdentityMapBreakage
 * ./run.sh -p IdentityMapBreakage
 * </pre>
 */
public class IdentityMapBreakage {

  static class Invoice {
    Integer net;
    Integer gross;
    LocalDate issued;
    LocalDate due;
    Invoice parent; // a real cycle, to show the detector still has a job
  }

  public static void main(String[] args) {
    var invoice = new Invoice();
    invoice.net = Integer.valueOf(1000);
    invoice.gross = Integer.valueOf(1000); // separately boxed, equal by value
    invoice.issued = LocalDate.of(2026, 9, 19);
    invoice.due = LocalDate.of(2026, 9, 19); // separately constructed, equal by value
    invoice.parent = invoice; // cycle

    Demo.section("1. serializer with identity-based back-references");
    System.out.println("  " + new GraphDumper().dump(invoice));

    Demo.section("2. footprint estimator: distinct objects reachable from a List<Integer>");
    var scores = IntStream.range(0, 1000).mapToObj(i -> Integer.valueOf(1000)).collect(Collectors.toCollection(ArrayList::new));
    System.out.printf("  list of 1000 separately boxed Integer(1000): %d distinct objects counted%n", countDistinct(scores));
  }

  /** Writes each object once; a second encounter becomes @ref<n>. A genuine cycle must be caught this way. */
  static class GraphDumper {
    final IdentityHashMap<Object, Integer> written = new IdentityHashMap<>();

    String dump(Object node) {
      if (node == null) {
        return "null";
      }
      var seen = written.get(node);
      if (seen != null) {
        return "@ref" + seen;
      }
      written.put(node, written.size() + 1);
      if (node instanceof Integer || node instanceof LocalDate || node instanceof String) {
        return String.valueOf(node);
      }
      return node.getClass().getSimpleName() + Stream.of(node.getClass().getDeclaredFields())
          .filter(f -> !Modifier.isStatic(f.getModifiers()))
          .map(f -> {
            try {
              f.setAccessible(true);
              return f.getName() + "=" + dump(f.get(node));
            } catch (IllegalAccessException e) {
              throw new IllegalStateException(e);
            }
          })
          .collect(Collectors.joining(", ", "{", "}"));
    }
  }

  static int countDistinct(List<?> roots) {
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    visited.add(roots);
    roots.forEach(visited::add);
    return visited.size();
  }
}
