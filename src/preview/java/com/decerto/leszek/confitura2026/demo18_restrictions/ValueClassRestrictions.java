package com.decerto.leszek.confitura2026.demo18_restrictions;

import com.decerto.leszek.confitura2026.Demo;
import com.decerto.leszek.confitura2026.Javac;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.ref.Cleaner;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import jdk.internal.value.ValueClass;

/**
 * What you cannot do with a value class - the language rules javac enforces on declarations, and
 * the identity-dependent operations the JVM refuses at runtime. Everything here is tried, not
 * quoted from the JEP.
 *
 * <pre>
 * ./run.sh -p ValueClassRestrictions
 * </pre>
 */
public class ValueClassRestrictions {

  value record Money(long amount, int currency) implements Serializable {}

  static class Bank {}

  public static void main(String[] args) throws Exception {
    Demo.section("javac: declaring a value class");
    javac("mutable field          ", "value class V { int x; void set(int v) { x = v; } }");
    javac("synchronized method    ", "value class V { synchronized void m() {} }");
    javac("extends identity class ", "class Bank {} value class V extends Bank {}");
    javac("subclass a value class ", "value class V {} class W extends V {}");
    javac("instance initializer   ", "value class V { final int x; { x = 1; } }");
    javac("this before fields set ", "value class V { final int x; V() { System.out.println(this); x = 1; } }");
    javac("ok: read after assign  ", "value class V { final int x; V() { x = 1; System.out.println(x); } }");
    javac("synchronized (value)   ", "value class V {} class U { void m(V v) { synchronized (v) {} } }");
    javac("ok: v.wait() (runtime)  ", "value class V {} class U { void m(V v) throws Exception { v.wait(); } }");
    javac("ok: abstract value base", "abstract value class Base { final int x; Base(int x) { this.x = x; } } value class V extends Base { V() { super(1); } }");

    Demo.section("JVM: identity operations");
    var money = new Money(100, 985);
    Object asObject = money;
    attempt("synchronized ((Object) money)      ", () -> {
      synchronized (asObject) {
        return "ok";
      }
    });
    attempt("Thread.holdsLock(money)            ", () -> String.valueOf(Thread.holdsLock(asObject)));
    attempt("asObject.wait()                    ", () -> {
      asObject.wait();
      return "ok";
    });
    attempt("new WeakReference<>(money)         ", () -> new WeakReference<>(money).toString());
    attempt("new SoftReference<>(money)         ", () -> new SoftReference<>(money).toString());
    attempt("new PhantomReference<>(money, q)   ", () -> new PhantomReference<>(money, new ReferenceQueue<>()).toString());
    attempt("WeakHashMap.put(money, 1)          ", () -> String.valueOf(new WeakHashMap<Object, Integer>().put(money, 1)));
    attempt("Cleaner.register(money, ...)       ", () -> Cleaner.create().register(money, () -> {}).toString());
    attempt("Objects.requireIdentity(money)     ", () -> Objects.requireIdentity(money).toString());
    attempt("System.identityHashCode(money)     ", () -> System.identityHashCode(money) + " (== identityHashCode(new Money(100, 985)): "
        + (System.identityHashCode(money) == System.identityHashCode(new Money(100, 985))) + ")");
    attempt("IdentityHashMap{money}.get(copy)   ", () -> {
      var map = new IdentityHashMap<Object, String>();
      map.put(money, "found");
      return String.valueOf(map.get(new Money(100, 985)));
    });

    Demo.section("JVM: reflection and handles");
    var amount = Money.class.getDeclaredField("amount");
    attempt("Field.setAccessible(true)          ", () -> {
      amount.setAccessible(true);
      return "ok";
    });
    attempt("Field.get(money)                   ", () -> String.valueOf(amount.get(money)));
    attempt("Field.set(money, 1L)               ", () -> {
      amount.set(money, 1L);
      return "ok, amount now " + money.amount();
    });
    var lookup = MethodHandles.privateLookupIn(Money.class, MethodHandles.lookup());
    attempt("Lookup.findGetter(...).invoke      ", () -> String.valueOf(lookup.findGetter(Money.class, "amount", long.class).invoke(money)));
    attempt("Lookup.findSetter(...)             ", () -> lookup.findSetter(Money.class, "amount", long.class).toString());
    attempt("Lookup.unreflectSetter(field)      ", () -> lookup.unreflectSetter(amount).toString());
    attempt("VarHandle.set(money, 1L)           ", () -> {
      lookup.findVarHandle(Money.class, "amount", long.class).set(money, 1L);
      return "ok";
    });
    attempt("VarHandle.compareAndSet(...)       ", () -> String.valueOf(lookup.findVarHandle(Money.class, "amount", long.class).compareAndSet(money, 100L, 1L)));
    attempt("sun.misc.Unsafe.objectFieldOffset  ", () -> String.valueOf(SUN_UNSAFE.objectFieldOffset(amount)));
    attempt("sun.misc.Unsafe.allocateInstance   ", () -> String.valueOf(SUN_UNSAFE.allocateInstance(Money.class)));
    attempt("Constructor.newInstance(100, 985)  ", () -> String.valueOf(Money.class.getDeclaredConstructor(long.class, int.class).newInstance(100L, 985)));
    attempt("Class.isValue()                    ", () -> "Money: " + Money.class.isValue() + ", Bank: " + Bank.class.isValue() + ", Object: " + Object.class.isValue());

    Demo.section("JVM: nulls and arrays");
    var nullRestricted = ValueClass.newNullRestrictedAtomicArray(Money.class, 2, money);
    attempt("(new Money[2])[0] = null            ", () -> {
      var nullable = new Money[2];
      nullable[0] = null;
      return "ok";
    });
    attempt("nullRestrictedArray[0] = null       ", () -> {
      nullRestricted[0] = null;
      return "ok";
    });
    attempt("nullRestrictedArray[0] = new Bank() ", () -> {
      ((Object[]) nullRestricted)[0] = new Bank();
      return "ok";
    });

    Demo.section("JVM: serialization of a Serializable value record");
    attempt("ObjectOutputStream.writeObject      ", () -> {
      var bytes = new ByteArrayOutputStream();
      try (var out = new ObjectOutputStream(bytes)) {
        out.writeObject(money);
      }
      var back = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())).readObject();
      return bytes.size() + " bytes, read back: " + back + ", == original: " + (back == money);
    });
  }

  static void javac(String what, String source) {
    var result = Javac.compile("Snippet", "public class Snippet { " + source + " }");
    System.out.printf("  %-24s %s%n", what, result.compiles() ? "compiles" : "REJECTED: " + String.join(" | ", result.errors()));
  }

  interface Operation {
    String run() throws Throwable;
  }

  static void attempt(String what, Operation operation) {
    String outcome;
    try {
      outcome = operation.run();
    } catch (Throwable t) {
      outcome = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage().lines().findFirst().orElse(""));
      outcome = outcome.replace(ValueClassRestrictions.class.getName() + "$", "").replaceAll(" \\(unnamed module.*\\)", "");
    }
    System.out.printf("  %-36s -> %s%n", what, outcome);
  }

  static final sun.misc.Unsafe SUN_UNSAFE = sunUnsafe();

  static sun.misc.Unsafe sunUnsafe() {
    try {
      var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (sun.misc.Unsafe) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
