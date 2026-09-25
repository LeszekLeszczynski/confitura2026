package com.decerto.leszek.confitura2026;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;

/** Tiny wrapper around JOL so every demo prints the same way. */
public final class Demo {

  static {
    // JOL prints a one-time "cannot attach the Serviceability Agent" notice on first use (macOS needs
    // sudo for it, and we never use the real addresses it would provide) - prime it quietly
    var out = System.out;
    try (var quiet = new PrintStream(OutputStream.nullOutputStream())) {
      System.setOut(quiet);
      VM.current();
    } finally {
      System.setOut(out);
    }
  }

  private Demo() {}

  public static void vm() {
    System.out.println(VM.current().details());
  }

  public static void layout(Class<?> type) {
    section(type.getName());
    System.out.println(ClassLayout.parseClass(type).toPrintable());
  }

  public static void layout(Object instance) {
    section(instance.getClass().getName() + " (instance)");
    System.out.println(ClassLayout.parseInstance(instance).toPrintable());
  }

  public static void graph(Object... roots) {
    section("object graph of " + Arrays.toString(roots));
    var graph = GraphLayout.parseInstance(roots);
    System.out.println(graph.toFootprint());
    System.out.println(graph.toPrintable());
  }

  /** Bytes allocated so far by the current thread; diff two calls to measure a block. */
  public static long allocatedBytes() {
    return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
        .getCurrentThreadAllocatedBytes();
  }

  /**
   * Asks the JVM whether an array is stored flat (elements inline) or as references. Uses
   * jdk.internal.value.ValueClass reflectively so this compiles without preview; at runtime it needs
   * --add-exports java.base/jdk.internal.value=ALL-UNNAMED (part of the preview profile).
   */
  public static boolean isFlatArray(Object[] array) {
    try {
      var valueClass = Class.forName("jdk.internal.value.ValueClass");
      return (boolean) valueClass.getMethod("isFlatArray", Object[].class).invoke(null, (Object) array);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --enable-preview --add-exports java.base/jdk.internal.value=ALL-UNNAMED", e);
    }
  }

  /** A plain reference array for a value class - what the JVM would use if it could not flatten. */
  @SuppressWarnings("unchecked")
  public static <T> T[] newReferenceArray(Class<T> elementType, int length) {
    try {
      var valueClass = Class.forName("jdk.internal.value.ValueClass");
      return (T[]) valueClass.getMethod("newReferenceArray", Class.class, int.class).invoke(null, elementType, length);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --enable-preview --add-exports java.base/jdk.internal.value=ALL-UNNAMED", e);
    }
  }

  /** Asks the JVM whether a field is stored flat (inline) or as a reference. */
  public static boolean isFlatField(Class<?> type, String fieldName) {
    try {
      var unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
      var unsafe = unsafeClass.getMethod("getUnsafe").invoke(null);
      var field = type.getDeclaredField(fieldName);
      return (boolean) unsafeClass.getMethod("isFlatField", Field.class).invoke(unsafe, field);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --add-exports java.base/jdk.internal.misc=ALL-UNNAMED", e);
    }
  }

  /** Does this field carry a null marker byte? False for references and null-restricted slots. */
  public static boolean hasNullMarker(Class<?> type, String fieldName) {
    try {
      var unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
      var unsafe = unsafeClass.getMethod("getUnsafe").invoke(null);
      var field = type.getDeclaredField(fieldName);
      return (boolean) unsafeClass.getMethod("hasNullMarker", Field.class).invoke(unsafe, field);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --add-exports java.base/jdk.internal.misc=ALL-UNNAMED", e);
    }
  }

  /** Shallow size as the JVM reports it (via Instrumentation), independent of JOL's field parsing. */
  public static long sizeOf(Object instance) {
    return VM.current().sizeOf(instance);
  }

  /** Like layout(), but understands flattened fields. */
  public static void shape(Object instance) {
    section(instance.getClass().getSimpleName() + " (shape)");
    System.out.println(ObjectShape.of(instance));
  }

  /** A null-free flat array: no null marker per element, so the payload alone decides the size. */
  @SuppressWarnings("unchecked")
  public static <T> T[] newNullRestrictedArray(Class<T> elementType, int length, T initialValue) {
    try {
      var valueClass = Class.forName("jdk.internal.value.ValueClass");
      return (T[]) valueClass
          .getMethod("newNullRestrictedAtomicArray", Class.class, int.class, Object.class)
          .invoke(null, elementType, length, initialValue);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --enable-preview --add-exports java.base/jdk.internal.value=ALL-UNNAMED", e);
    }
  }

  public static void section(String title) {
    System.out.println();
    System.out.println("=== " + title + " ===");
  }
}
