package com.decerto.leszek.confitura2026.demo10_frameworks;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;
import sun.misc.Unsafe;

/**
 * Which object-instantiation strategies used by frameworks still work on a value class? Hibernate,
 * Jackson, Kryo and Java serialization all build objects behind your back; a 3 x 3 matrix of
 * strategy x target type, each cell filled empirically.
 *
 * <pre>
 * ./run.sh -p FrameworkInstantiation
 * </pre>
 */
public class FrameworkInstantiation {

  static final long AMOUNT = 100L;
  static final int CURRENCY = 985; // PLN

  /** 1. classic mutable POJO: no-arg constructor (+ an all-args one, as Lombok would add). */
  public static class MoneyPojo {
    long amount;
    int currency;

    public MoneyPojo() {}

    public MoneyPojo(long amount, int currency) {
      this.amount = amount;
      this.currency = currency;
    }

    @Override
    public String toString() {
      return "MoneyPojo[amount=" + amount + ", currency=" + currency + "]";
    }
  }

  /** 2. record. */
  public record MoneyRecord(long amount, int currency) {}

  /** 3. value record. */
  public value record MoneyValue(long amount, int currency) {}

  /** 4. value class shaped like a Hibernate @Embeddable: no-arg + all-args constructors. */
  public static value class MoneyValueClass {
    final long amount;
    final int currency;

    public MoneyValueClass() {
      this(0, 0);
    }

    public MoneyValueClass(long amount, int currency) {
      this.amount = amount;
      this.currency = currency;
    }

    @Override
    public String toString() {
      return "MoneyValueClass[amount=" + amount + ", currency=" + currency + "]";
    }
  }

  record Strategy(String name, Function<Class<?>, Object> instantiate) {}

  public static void main(String[] args) throws Exception {
    // first sun.misc.Unsafe memory access prints a one-time JVM warning; get it out of the way
    UNSAFE.objectFieldOffset(MoneyPojo.class.getDeclaredField("amount"));

    var strategies = List.of(
        new Strategy("A  Unsafe.allocateInstance + Unsafe.put*  (Kryo)", FrameworkInstantiation::unsafeAllocate),
        new Strategy("B  no-arg ctor + Field.set                (Hibernate POJO)", FrameworkInstantiation::noArgConstructorAndFieldSet),
        new Strategy("C  Constructor.newInstance(values)        (Hibernate 6 / Jackson)", FrameworkInstantiation::allArgsConstructor));
    var types = List.<Class<?>>of(MoneyPojo.class, MoneyRecord.class, MoneyValue.class, MoneyValueClass.class);

    for (var strategy : strategies) {
      System.out.println();
      System.out.println(strategy.name());
      for (var type : types) {
        System.out.printf("   %-16s %s%n", type.getSimpleName(), outcome(strategy, type));
      }
    }
  }

  static String outcome(Strategy strategy, Class<?> type) {
    try {
      var instance = strategy.instantiate().apply(type);
      var ok = instance.toString().contains("amount=" + AMOUNT) && instance.toString().contains("currency=" + CURRENCY);
      return ok ? "OK  " + instance.toString().replaceAll("^\\w+", "") : "WRONG " + instance;
    } catch (Throwable t) {
      var cause = t.getCause() != null && t instanceof RuntimeException ? t.getCause() : t;
      return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }
  }

  // --- A: bypass constructors entirely, poke bytes into the object ---
  static Object unsafeAllocate(Class<?> type) {
    try {
      var instance = UNSAFE.allocateInstance(type);
      UNSAFE.putLong(instance, UNSAFE.objectFieldOffset(type.getDeclaredField("amount")), AMOUNT);
      UNSAFE.putInt(instance, UNSAFE.objectFieldOffset(type.getDeclaredField("currency")), CURRENCY);
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  // --- B: default constructor, then write the fields reflectively ---
  static Object noArgConstructorAndFieldSet(Class<?> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      var instance = constructor.newInstance();
      set(type.getDeclaredField("amount"), instance, AMOUNT);
      set(type.getDeclaredField("currency"), instance, CURRENCY);
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  static void set(Field field, Object instance, Object value) throws IllegalAccessException {
    field.setAccessible(true);
    field.set(instance, value);
  }

  // --- C: the canonical / all-args constructor ---
  static Object allArgsConstructor(Class<?> type) {
    try {
      return type.getDeclaredConstructor(long.class, int.class).newInstance(AMOUNT, CURRENCY);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  static final Unsafe UNSAFE = theUnsafe();

  static Unsafe theUnsafe() {
    try {
      var field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (Unsafe) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
