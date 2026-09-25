package com.decerto.leszek.confitura2026.demo12_bypass;

import com.decerto.leszek.confitura2026.Demo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

/**
 * Can a value object exist without its constructor ever having run? A null-restricted field or
 * array needs *some* value in every slot from the moment the memory exists, so the JVM must have a
 * default instance up its sleeve. This demo tries every route by which such an instance could leak
 * out and reports what happens on this build - a constructor counter tells the truth.
 *
 * <pre>
 * ./run.sh -p DefaultValueBypass
 * </pre>
 */
public class DefaultValueBypass {

  static int constructorCalls;

  /** Every value a constructor ever produced; anything observed outside this set was fabricated. */
  static final Set<String> constructed = new HashSet<>();

  /** The user-visible contract: negative amounts and bad currencies must never exist. */
  value record Money(long amount, int currency) {
    Money {
      constructorCalls++;
      if (amount < 0) throw new IllegalArgumentException("negative: " + amount);
      if (currency <= 0) throw new IllegalArgumentException("bad currency");
      constructed.add("Money[amount=" + amount + ", currency=" + currency + "]"); // no `this` yet
    }
  }

  /** Same contract, 8 bytes instead of 12 so it can actually be flattened when null-restricted. */
  value record SmallMoney(int amount, int currency) {
    SmallMoney {
      constructorCalls++;
      if (amount < 0) throw new IllegalArgumentException("negative: " + amount);
      if (currency <= 0) throw new IllegalArgumentException("bad currency");
      constructed.add("SmallMoney[amount=" + amount + ", currency=" + currency + "]");
    }
  }

  static class Account implements Serializable {
    @NullRestricted final Money balance;

    Account(Money balance) {
      this.balance = balance;
      super();
    }
  }

  static class SmallAccount implements Serializable {
    @NullRestricted final SmallMoney balance;

    SmallAccount(SmallMoney balance) {
      this.balance = balance;
      super();
    }
  }

  static final List<String> fabricated = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--crash-probe")) {
      crashProbe();
      return;
    }

    Demo.section("0. the constructor does its job");
    route("new Money(-1, 985)", () -> new Money(-1, 985));
    var valid = route("new Money(100, 985)", () -> new Money(100, 985));
    var smallValid = route("new SmallMoney(100, 985)", () -> new SmallMoney(100, 985));
    System.out.printf("  Money flat as @NullRestricted field: %s, SmallMoney: %s%n",
        Demo.isFlatField(Account.class, "balance"), Demo.isFlatField(SmallAccount.class, "balance"));

    Demo.section("1. ValueClass.newNullRestricted*Array(Class, int, Object initialValue)");
    route("newNullRestrictedAtomicArray(Money.class, 3, null)",
        () -> ValueClass.newNullRestrictedAtomicArray(Money.class, 3, null));
    route("newNullRestrictedAtomicArray(Money.class, 3, valid)[0]",
        () -> ValueClass.newNullRestrictedAtomicArray(Money.class, 3, valid)[0]);
    route("newNullRestrictedNonAtomicArray(Money.class, 3, null)",
        () -> ValueClass.newNullRestrictedNonAtomicArray(Money.class, 3, null));
    route("newNullRestrictedAtomicArray(SmallMoney.class, 3, smallValid)[2]  (flat)",
        () -> ValueClass.newNullRestrictedAtomicArray(SmallMoney.class, 3, smallValid)[2]);
    route("newNullableAtomicArray(SmallMoney.class, 3)[0]",
        () -> ValueClass.newNullableAtomicArray(SmallMoney.class, 3)[0]);
    route("(new SmallMoney[3])[0]", () -> (new SmallMoney[3])[0]);

    Demo.section("2. a @NullRestricted field that was never assigned");
    javac("UnassignedField", "@NullRestricted final M m;");
    javac("ReadBeforeAssign", "@NullRestricted final M m; ReadBeforeAssign() { System.out.println(m); m = new M(1); super(); }");
    javac("AssignedAfterSuper", "@NullRestricted final M m; AssignedAfterSuper() { super(); m = new M(1); }");
    javac("NonFinalField", "@NullRestricted M m;");
    var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
    route("internal Unsafe.allocateInstance(SmallAccount.class).balance  (flat field)",
        () -> ((SmallAccount) unsafe.allocateInstance(SmallAccount.class)).balance);
    route("internal Unsafe.allocateInstance(Account.class).balance  (reference field)",
        () -> ((Account) unsafe.allocateInstance(Account.class)).balance);
    route("sun.misc.Unsafe.allocateInstance(SmallAccount.class).balance",
        () -> ((SmallAccount) SUN_UNSAFE.allocateInstance(SmallAccount.class)).balance);
    route("ReflectionFactory.newConstructorForSerialization(SmallAccount).newInstance().balance",
        () -> ((SmallAccount) sun.reflect.ReflectionFactory.getReflectionFactory()
            .newConstructorForSerialization(SmallAccount.class, Object.class.getDeclaredConstructor())
            .newInstance()).balance);
    route("ObjectInputStream round-trip of new SmallAccount(smallValid)", () -> {
      var bytes = new ByteArrayOutputStream();
      try (var out = new ObjectOutputStream(bytes)) {
        out.writeObject(new SmallAccount(smallValid));
      }
      return ((SmallAccount) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject()).balance;
    });

    Demo.section("3. Unsafe.newSpecialArray(Class, int, int layoutKind) - no initial value parameter");
    route("internal Unsafe.newSpecialArray(Money.class, 3, 0)", () -> unsafe.newSpecialArray(Money.class, 3, 0));
    forkedRoute("internal Unsafe.newSpecialArray(Money.class, 3, 1)  [forked JVM]");

    Demo.section("4. Unsafe.allocateInstance on the value class itself");
    route("jdk.internal.misc.Unsafe.allocateInstance(Money.class)", () -> unsafe.allocateInstance(Money.class));
    route("sun.misc.Unsafe.allocateInstance(Money.class)  (no --add-exports needed)",
        () -> SUN_UNSAFE.allocateInstance(Money.class));
    route("Class.forName(Money).getDeclaredConstructor().newInstance()  (no no-arg ctor)",
        () -> Money.class.getDeclaredConstructor().newInstance());

    Demo.section("tally");
    System.out.printf("  validating constructors ran:                 %d times%n", constructorCalls);
    System.out.printf("  distinct values produced by constructors:    %d%n", constructed.size());
    System.out.printf("  values observed that no constructor produced: %d%n", fabricated.size());
    fabricated.forEach(f -> System.out.println("    - " + f));
  }

  /** Runs one route; reports the result and whether a constructor ran during it. */
  static <T> T route(String name, Callable<T> attempt) {
    var before = constructorCalls;
    T result = null;
    String outcome;
    try {
      result = attempt.call();
      outcome = String.valueOf(result);
    } catch (Throwable t) {
      var cause = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
      outcome = cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage().lines().findFirst().orElse(""));
    }
    var ran = constructorCalls - before;
    System.out.printf("  %-78s -> %s%n", name, outcome);
    System.out.printf("  %-78s    constructor ran: %s%n", "", ran > 0 ? ran + "x" : "NO");
    if ((result instanceof Money || result instanceof SmallMoney) && !constructed.contains(result.toString())) {
      fabricated.add(result + " via " + name);
    }
    return result;
  }

  /** Compiles a one-class snippet with javac in-process, and if it compiles, tries to load and instantiate it. */
  static void javac(String className, String body) throws Exception {
    var source = "import jdk.internal.vm.annotation.NullRestricted; public class " + className
        + " { public value record M(int a) {} " + body + " }";
    var outDir = Files.createTempDirectory("bypass");
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return source;
      }
    };
    var options = List.of("--enable-preview", "-source", "28", "-proc:none",
        "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED", "-d", outDir.toString());
    var compiled = ToolProvider.getSystemJavaCompiler().getTask(null, null, diagnostics, options, null, List.of(file)).call();
    var errors = diagnostics.getDiagnostics().stream()
        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
        .map(d -> d.getMessage(null))
        .toList();
    System.out.printf("  javac %-22s %-30s -> %s%n", className, "{ " + body + " }", compiled ? "compiles" : "REJECTED " + errors);
    if (compiled) {
      route("      load + new " + className + "()", () -> {
        try (var loader = new URLClassLoader(new java.net.URL[] {outDir.toUri().toURL()})) {
          var constructor = Class.forName(className, true, loader).getDeclaredConstructor();
          constructor.setAccessible(true);
          return constructor.newInstance();
        }
      });
    }
  }

  /** The route that takes the JVM down: run it in a child JVM and report what came back. */
  static void forkedRoute(String name) throws Exception {
    var java = ProcessHandle.current().info().command().orElseThrow();
    var command = new ArrayList<String>();
    command.add(java);
    command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
    var errorFile = Files.createTempFile("bypass-hs_err", ".log");
    command.addAll(List.of("-XX:ErrorFile=" + errorFile, "-cp", System.getProperty("java.class.path"),
        DefaultValueBypass.class.getName(), "--crash-probe"));
    var process = new ProcessBuilder(command).redirectErrorStream(true).start();
    var output = new String(process.getInputStream().readAllBytes());
    var exit = process.waitFor();
    var headline = Stream.of(output.split("\n"))
        .filter(line -> line.contains("Internal Error") || line.contains("Exception") || line.startsWith("result:"))
        .findFirst()
        .orElse(output.strip());
    System.out.printf("  %-78s -> exit code %d: %s%n", name, exit, headline.strip());
    Files.deleteIfExists(errorFile);
  }

  static void crashProbe() {
    var array = jdk.internal.misc.Unsafe.getUnsafe().newSpecialArray(Money.class, 3, 1);
    System.out.println("result: " + array[0] + " (constructor ran: " + (constructorCalls > 0) + ")");
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
