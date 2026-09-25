package com.decerto.leszek.confitura2026.demo14_identity_breaks;

import com.decerto.leszek.confitura2026.Demo;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Two places where "one object, one identity" is not cosmetic but load-bearing. Each is shown with
 * an identity class and a value class side by side, in the same run.
 *
 * <pre>
 * ./run.sh -p IdentityAssumptions
 * </pre>
 */
public class IdentityAssumptions {

  public static void main(String[] args) {
    Demo.section("1. tree-only serializer: shared references are an error");
    record Day(int year, int month, int day) {}
    class Order {
      Day created = new Day(2026, 9, 19);
      Day updated = new Day(2026, 9, 19);
    }
    class OrderOnJdk28 {
      LocalDate created = LocalDate.of(2026, 9, 19);
      LocalDate updated = LocalDate.of(2026, 9, 19);
    }
    attempt("Order with two equal identity-record dates", () -> new TreeSerializer().write(new Order()));
    attempt("Order with two equal LocalDates          ", () -> new TreeSerializer().write(new OrderOnJdk28()));

    Demo.section("2. capability registry: the reference IS the permission");
    var identityVault = new Vault<>(IdentityToken::new);
    var valueVault = new Vault<>(ValueToken::new);
    for (var vault : new Vault[] {identityVault, valueVault}) {
      var genuine = vault.issue("alice");
      attempt("  genuine token, " + genuine.getClass().getSimpleName(), () -> vault.withdraw(genuine, 10));
      var forged = vault.forge(((Token) genuine).id()); // attacker guesses the sequential id
      attempt("  forged  token, " + forged.getClass().getSimpleName(), () -> vault.withdraw(forged, 1_000_000));
    }
  }

  // --- scenario 1 ---------------------------------------------------------------------------------

  /** Writes a JSON-like tree; the format has no way to express sharing, so sharing is rejected. */
  static class TreeSerializer {
    final Set<Object> onTheWay = Collections.newSetFromMap(new IdentityHashMap<>());
    final IdentityHashMap<Object, String> firstSeenAt = new IdentityHashMap<>();

    String write(Object node) {
      return write(node, "$");
    }

    String write(Object node, String path) {
      if (node == null || node instanceof Number || node instanceof String) {
        return String.valueOf(node);
      }
      var earlier = firstSeenAt.putIfAbsent(node, path);
      if (earlier != null) {
        throw new IllegalStateException("shared reference: " + node + " at " + earlier + " and " + path);
      }
      if (node instanceof LocalDate || node.getClass().isRecord()) {
        return "\"" + node + "\"";
      }
      return Stream.of(node.getClass().getDeclaredFields())
          .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
          .map(f -> {
            try {
              f.setAccessible(true);
              return "\"" + f.getName() + "\": " + write(f.get(node), path + "." + f.getName());
            } catch (IllegalAccessException e) {
              throw new IllegalStateException(e);
            }
          })
          .collect(Collectors.joining(", ", "{", "}"));
    }
  }

  // --- scenario 2 ---------------------------------------------------------------------------------

  interface Token {
    long id();
  }

  record IdentityToken(long id) implements Token {}

  value record ValueToken(long id) implements Token {}

  /**
   * Object-capability style: a token is valid because the vault handed *that object* out, so it keeps
   * the issued instances in an identity set. Knowing the id is not supposed to be enough.
   */
  static class Vault<T extends Token> {
    final Set<T> issued = Collections.newSetFromMap(new IdentityHashMap<>());
    final AtomicLong nextId = new AtomicLong(1);
    final java.util.function.LongFunction<T> constructor;
    long balance = 1_000_000;

    Vault(java.util.function.LongFunction<T> constructor) {
      this.constructor = constructor;
    }

    T issue(String owner) {
      var token = constructor.apply(nextId.getAndIncrement());
      issued.add(token);
      return token;
    }

    T forge(long id) {
      return constructor.apply(id);
    }

    String withdraw(T token, long amount) {
      if (!issued.contains(token)) {
        throw new SecurityException("unknown token " + token);
      }
      balance -= amount;
      return "withdrew " + amount + ", balance now " + balance;
    }
  }

  interface Operation {
    String run();
  }

  static void attempt(String what, Operation operation) {
    String outcome;
    try {
      outcome = operation.run();
    } catch (RuntimeException e) {
      outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    System.out.printf("  %-46s -> %s%n", what, outcome);
  }
}
