package com.decerto.leszek.confitura2026.demo11_entity;

import com.decerto.leszek.confitura2026.Demo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import sun.misc.Unsafe;

/**
 * Is an ordinary JPA entity still safe when Integer, Long and LocalDate become value classes? The
 * entity itself stays an identity class; only its field types changed. Hydrate it the ways ORMs do
 * and check what still holds. Same class, run plain and with preview.
 *
 * <pre>
 * ./run.sh    ValueFieldsInEntity
 * ./run.sh -p ValueFieldsInEntity
 * </pre>
 */
public class ValueFieldsInEntity {

  /** The usual thing: mutable, no-arg constructor, field access, Serializable. */
  public static class Order implements Serializable {
    Long id;
    Integer amount;
    Short quantity;
    Double price;
    LocalDate createdOn;
    LocalDateTime updatedAt;
    String name;

    public Order() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof Order that
          && Objects.equals(id, that.id)
          && Objects.equals(amount, that.amount)
          && Objects.equals(quantity, that.quantity)
          && Objects.equals(price, that.price)
          && Objects.equals(createdOn, that.createdOn)
          && Objects.equals(updatedAt, that.updatedAt)
          && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, amount, quantity, price, createdOn, updatedAt, name);
    }

    @Override
    public String toString() {
      return "Order[id=" + id + ", amount=" + amount + ", quantity=" + quantity + ", price=" + price
          + ", createdOn=" + createdOn + ", updatedAt=" + updatedAt + ", name=" + name + "]";
    }
  }

  static final List<String> FIELDS = List.of("id", "amount", "quantity", "price", "createdOn", "updatedAt", "name");
  static final Object[] VALUES = {7L, 1000, (short) 3, 9.99, LocalDate.of(2026, 9, 19), LocalDateTime.of(2026, 9, 19, 10, 0), "demo"};

  public static void main(String[] args) throws Throwable {
    var expected = hydrateWithFieldSet();

    Demo.section("field layout inside the entity");
    FIELDS.forEach(f -> System.out.printf("  %-10s %-14s flat: %s%n", f, fieldType(f), Demo.isFlatField(Order.class, f)));
    Demo.shape(expected);

    Demo.section("1. no-arg ctor + Field.set (Hibernate field access)");
    System.out.println("  " + expected);
    System.out.println("  Field.get round-trip equal: " + equalsViaFieldGet(expected));
    var withNulls = new Order();
    set(withNulls, "amount", null);
    System.out.println("  Field.set(null) on Integer field -> amount=" + withNulls.amount);

    Demo.section("2. VarHandle writes (bytecode enhancement style)");
    var viaVarHandles = new Order();
    for (var i = 0; i < FIELDS.size(); i++) {
      var handle = MethodHandles.privateLookupIn(Order.class, MethodHandles.lookup())
          .findVarHandle(Order.class, FIELDS.get(i), fieldClass(FIELDS.get(i)));
      handle.set(viaVarHandles, VALUES[i]);
    }
    System.out.println("  equal to expected: " + expected.equals(viaVarHandles));

    Demo.section("3. sun.misc.Unsafe.putObject/getObject by field offset (Kryo, Objenesis-style libs)");
    var viaUnsafe = new Order();
    for (var i = 0; i < FIELDS.size(); i++) {
      var offset = UNSAFE.objectFieldOffset(Order.class.getDeclaredField(FIELDS.get(i)));
      UNSAFE.putObject(viaUnsafe, offset, VALUES[i]);
      var readBack = attempt(() -> String.valueOf(UNSAFE.getObject(viaUnsafe, offset)));
      System.out.printf("  %-10s put %-18s -> field now: %-18s  Unsafe.getObject: %s%n",
          FIELDS.get(i), VALUES[i], get(viaUnsafe, FIELDS.get(i)), readBack);
    }
    System.out.println("  equal to expected: " + expected.equals(viaUnsafe));

    Demo.section("4. Java serialization round-trip");
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(expected);
    }
    var deserialized = (Order) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
    System.out.println("  equal to expected: " + expected.equals(deserialized));

    Demo.section("5. dirty checking: loaded snapshot vs modified copy, amount 1000 in both");
    var snapshot = hydrateWithFieldSet();
    set(snapshot, "amount", Integer.valueOf(1000)); // a separately boxed 1000 (outside the -128..127 cache)
    System.out.println("  snapshot.amount.equals(entity.amount): " + snapshot.amount.equals(expected.amount));
    System.out.println("  snapshot.amount == entity.amount:      " + (snapshot.amount == expected.amount));

    Demo.section("6. things people do with entity ids and values");
    // with preview, javac rejects synchronized(order.id) outright: "required: identity class"
    Object lock = expected.id;
    System.out.println("  synchronized (order.id)          -> " + attempt(() -> {
      synchronized (lock) {
        return "ok";
      }
    }));
    System.out.println("  new WeakHashMap<Long, Order>()   -> " + attempt(() -> {
      var cache = new WeakHashMap<Long, Order>();
      cache.put(expected.id, expected);
      return "ok, size " + cache.size();
    }));
    System.out.println("  new WeakReference<>(order.createdOn) -> " + attempt(() -> {
      new WeakReference<>(expected.createdOn);
      return "ok";
    }));
  }

  static Order hydrateWithFieldSet() throws ReflectiveOperationException {
    var order = Order.class.getDeclaredConstructor().newInstance();
    for (var i = 0; i < FIELDS.size(); i++) {
      set(order, FIELDS.get(i), VALUES[i]);
    }
    return order;
  }

  static boolean equalsViaFieldGet(Order order) throws ReflectiveOperationException {
    for (var i = 0; i < FIELDS.size(); i++) {
      if (!Objects.equals(get(order, FIELDS.get(i)), VALUES[i])) {
        return false;
      }
    }
    return true;
  }

  static void set(Order order, String field, Object value) throws ReflectiveOperationException {
    var f = Order.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(order, value);
  }

  static Object get(Order order, String field) throws ReflectiveOperationException {
    var f = Order.class.getDeclaredField(field);
    f.setAccessible(true);
    return f.get(order);
  }

  static Class<?> fieldClass(String field) throws NoSuchFieldException {
    return Order.class.getDeclaredField(field).getType();
  }

  static String fieldType(String field) {
    try {
      return fieldClass(field).getSimpleName();
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException(field, e);
    }
  }

  interface Operation {
    String run() throws Throwable;
  }

  static String attempt(Operation operation) {
    try {
      return operation.run();
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
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
