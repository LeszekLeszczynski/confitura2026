package com.decerto.leszek.confitura2026.demo16_sizeof;

import com.decerto.leszek.confitura2026.Demo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.sizeof.SizeOf;

/**
 * A real library, unmodified: Ehcache 3.12.0. Its byte-sized heap tiers charge each entry with a
 * bundled sizeof engine that walks the object graph with an IdentityHashMap of visited objects and
 * adds up their sizes. Nothing throws. Same code, run plain and with preview.
 *
 * <pre>
 * ./run.sh    CacheSizing
 * ./run.sh -p CacheSizing
 * </pre>
 */
public class CacheSizing {

  static final int VALUES_PER_ENTRY = 100;
  static final long BUDGET = 200 * 1024;

  public static void main(String[] args) {
    var sizeOf = SizeOf.newInstance();
    System.out.println("strategy: " + sizeOf.getClass().getSimpleName());

    Demo.section("what a List<Integer> of 1000 separately boxed Integer.valueOf(1000) really weighs");
    var list = boxedList(1000);
    itemize(list);
    System.out.printf("  %-34s %,12d bytes%n", "reported by Ehcache sizeof:", sizeOf.deepSizeOf(list));
    System.out.printf("  %-34s %,12d bytes%n", "of which it actually counted:", sizeOf.deepSizeOf(list) - Demo.sizeOf(list) - Demo.sizeOf(list.toArray()));
    System.out.println("    ^ that is one Integer, not one thousand: the identity set treats every equal value as the same object");

    Demo.section("cache with a " + BUDGET / 1024 + " KB heap budget, entries = List<Integer> of " + VALUES_PER_ENTRY + " equal values");
    var sample = boxedList(VALUES_PER_ENTRY);
    System.out.printf("  one entry really weighs %,d bytes; Ehcache sizeof charges it %,d%n", trueSize(sample), sizeOf.deepSizeOf(sample));
    var admitted = new ArrayList<List<Integer>>();
    var charged = 0L;
    var held = 0L;
    while (true) {
      var entry = boxedList(VALUES_PER_ENTRY);
      var cost = sizeOf.deepSizeOf(entry);
      if (charged + cost > BUDGET) {
        break;
      }
      charged += cost;
      held += trueSize(entry);
      admitted.add(entry);
    }
    System.out.printf("  entries admitted:        %,8d%n", admitted.size());
    System.out.printf("  budget charged:          %,8d bytes%n", charged);
    System.out.printf("  really held on the heap: %,8d bytes  (%.0f%% of budget)%n", held, 100.0 * held / BUDGET);

    Demo.section("the real thing: Ehcache 3.12.0, heap(" + BUDGET / 1024 + ", MemoryUnit.KB), 2000 puts of the same entries");
    try (var manager = CacheManagerBuilder.newCacheManagerBuilder().build(true)) {
      var cache = manager.createCache("orders", CacheConfigurationBuilder.newCacheConfigurationBuilder(
          Integer.class, List.class, ResourcePoolsBuilder.newResourcePoolsBuilder().heap(BUDGET / 1024, MemoryUnit.KB)));
      IntStream.range(0, 2000).forEach(i -> cache.put(i, boxedList(VALUES_PER_ENTRY)));
      var retained = 0L;
      var reallyHeld = 0L;
      for (var entry : cache) {
        retained++;
        @SuppressWarnings("unchecked")
        var value = (List<Integer>) entry.getValue();
        reallyHeld += trueSize(value);
      }
      System.out.printf("  entries retained:        %,8d%n", retained);
      System.out.printf("  really held on the heap: %,8d bytes  (%.0f%% of budget, values only)%n", reallyHeld, 100.0 * reallyHeld / BUDGET);
    }
  }

  /** Prints the parts a list of boxed values is made of. Every element is a heap object - an ArrayList's Object[] cannot be flat - so each one counts. */
  static void itemize(List<Integer> list) {
    var shell = Demo.sizeOf(list);
    var array = Demo.sizeOf(list.toArray());
    var element = Demo.sizeOf(list.get(0));
    System.out.printf("  %-34s %5d x %,6d = %,10d bytes%n", "ArrayList shell", 1, shell, shell);
    System.out.printf("  %-34s %5d x %,6d = %,10d bytes%n", "Object[" + list.size() + "] backing array", 1, array, array);
    System.out.printf("  %-34s %5d x %,6d = %,10d bytes%n", "Integer (header + int + padding)", list.size(), element, element * list.size());
    System.out.printf("  %-34s %,28d bytes%n", "really on the heap:", trueSize(list));
  }

  /** No identity tricks: every element is a heap object (an ArrayList cannot be flat), so count each. */
  static long trueSize(List<Integer> list) {
    return Demo.sizeOf(list) + Demo.sizeOf(list.toArray()) + list.stream().mapToLong(Demo::sizeOf).sum();
  }

  static List<Integer> boxedList(int n) {
    var list = new ArrayList<Integer>(n);
    IntStream.range(0, n).forEach(i -> list.add(Integer.valueOf(1000)));
    return list;
  }
}
