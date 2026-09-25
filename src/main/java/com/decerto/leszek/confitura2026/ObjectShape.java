package com.decerto.leszek.confitura2026;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.openjdk.jol.vm.VM;

/**
 * JOL-style "object internals" table that understands flattened value-class fields: an inlined
 * field is shown with its payload size and its own fields nested underneath. Offsets and flatness
 * come straight from the JVM (jdk.internal.misc.Unsafe), the total size from Instrumentation.
 */
public final class ObjectShape {

  private record Row(long offset, long size, String type, String description) {}

  private final Object unsafe;
  private final Class<?> unsafeClass;

  private ObjectShape() {
    try {
      unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
      unsafe = unsafeClass.getMethod("getUnsafe").invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("run with --add-exports java.base/jdk.internal.misc=ALL-UNNAMED", e);
    }
  }

  public static String of(Object instance) {
    return new ObjectShape().render(instance);
  }

  private String render(Object instance) {
    var type = instance.getClass();
    var headerSize = type.isValue() ? valueHeaderSize(type) : VM.current().objectHeaderSize();
    var rows = new ArrayList<Row>();
    rows.add(new Row(0, headerSize, "", "(object header)"));
    instanceFields(type).forEach(field -> addField(rows, field, fieldOffset(field), type.getSimpleName() + ".", ""));
    rows.sort(Comparator.comparingLong(Row::offset));

    var size = VM.current().sizeOf(instance);
    var out = new StringBuilder();
    out.append(type.getName()).append(" object internals:\n");
    out.append(String.format("%4s %3s  %-14s %s%n", "OFF", "SZ", "TYPE", "DESCRIPTION"));
    var cursor = 0L;
    for (var row : rows) {
      if (row.offset() > cursor) {
        out.append(String.format("%4d %3d  %-14s %s%n", cursor, row.offset() - cursor, "", "(gap)"));
      }
      out.append(String.format("%4d %3d  %-14s %s%n", row.offset(), row.size(), row.type(), row.description()));
      cursor = Math.max(cursor, row.offset() + row.size());
    }
    if (size > cursor) {
      out.append(String.format("%4d %3d  %-14s %s%n", cursor, size - cursor, "", "(alignment" + (type.isValue() ? " / own null marker)" : ")")));
    }
    out.append("Instance size: ").append(size).append(" bytes\n");
    return out.toString();
  }

  /** Adds a field row; a flat field also gets its value class's fields nested at their real offsets. */
  private void addField(List<Row> rows, Field field, long offset, String owner, String indent) {
    var type = field.getType();
    if (isFlatField(field)) {
      var payload = payloadSize(type);
      var nullRestricted = isNullRestrictedField(field);
      rows.add(new Row(offset, payload, type.getSimpleName(), indent + owner + field.getName()
          + " (flat" + (nullRestricted ? ", null-free)" : ", nullable)")));
      // HotSpot reports the marker offset for a top-level field; for nested ones it is relative to a layout we
      // cannot see from here (often tucked into padding), so only the slot size accounts for it
      if (!nullRestricted && indent.isEmpty()) {
        rows.add(new Row(nullMarkerOffset(field), 1, "", "  (null marker)"));
      }
      var header = valueHeaderSize(type);
      instanceFields(type).forEach(inner ->
          addField(rows, inner, offset + fieldOffset(inner) - header, "", indent + "  "));
    } else {
      rows.add(new Row(offset, VM.current().sizeOfField(type.getName()), type.getSimpleName(),
          indent + owner + field.getName() + (type.isPrimitive() ? "" : " (reference)")));
    }
  }

  /** Bytes the value class's fields span in its buffered (heap) form, header excluded. */
  private long payloadSize(Class<?> valueType) {
    var header = valueHeaderSize(valueType);
    return instanceFields(valueType)
        .mapToLong(f -> fieldOffset(f) - header + (isFlatField(f) ? payloadSize(f.getType()) : VM.current().sizeOfField(f.getType().getName())))
        .max()
        .orElse(0);
  }

  private static Stream<Field> instanceFields(Class<?> type) {
    return type == null || type == Object.class
        ? Stream.empty()
        : Stream.concat(
            instanceFields(type.getSuperclass()),
            Stream.of(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())));
  }

  private long fieldOffset(Field field) {
    return (long) call("objectFieldOffset", new Class<?>[] {Field.class}, field);
  }

  private long nullMarkerOffset(Field field) {
    return (int) call("nullMarkerOffset", new Class<?>[] {Field.class}, field);
  }

  private boolean isFlatField(Field field) {
    return (boolean) call("isFlatField", new Class<?>[] {Field.class}, field);
  }

  private long valueHeaderSize(Class<?> valueType) {
    return (long) call("valueHeaderSize", new Class<?>[] {Class.class}, valueType);
  }

  private static boolean isNullRestrictedField(Field field) {
    try {
      var valueClass = Class.forName("jdk.internal.value.ValueClass");
      return (boolean) valueClass.getMethod("isNullRestrictedField", Field.class).invoke(null, field);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object call(String method, Class<?>[] parameterTypes, Object... args) {
    try {
      return unsafeClass.getMethod(method, parameterTypes).invoke(unsafe, args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
