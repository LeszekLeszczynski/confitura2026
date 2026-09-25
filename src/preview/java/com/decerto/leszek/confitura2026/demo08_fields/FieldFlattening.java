package com.decerto.leszek.confitura2026.demo08_fields;

import com.decerto.leszek.confitura2026.Demo;
import java.time.LocalDate;
import jdk.internal.vm.annotation.NullRestricted;

/**
 * Flattening in an ordinary (reassignable) field follows the same rule as arrays: payload + null
 * marker must fit in one atomic 64-bit access, so a Point(int, int) field in a class is NOT flat. A
 * strict field - one that is assigned before super() and never again, which is what javac emits for
 * record components and value-class fields - cannot be torn, and the JVM inlines it regardless of
 * size. Null-restriction (JDK-internal @NullRestricted for now) removes the null marker on top.
 * Flattening is recursive: a value object inside a value object inside a class ends up as one
 * contiguous block of bytes.
 *
 * <pre>
 * ./run.sh -p FieldFlattening
 * ./run.sh -p FieldFlattening -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout
 * </pre>
 */
public class FieldFlattening {

  static final int N = 1_000_000;

  record IdentityPoint(int x, int y) {}

  value record Point(int x, int y) {}

  value record Rgb(byte r, byte g, byte b) {}

  /** A value object holding a value object; a pixel always has a colour, so rgb is null-free. */
  value record Pixel(@NullRestricted Rgb rgb, byte alpha) {
    static Pixel of(int i) {
      return new Pixel(new Rgb((byte) i, (byte) (i >> 8), (byte) (i >> 16)), (byte) 255);
    }
  }

  /** Same 4 bytes of data as Pixel, but without nesting - no inner slot to pad. */
  value record PackedPixel(byte r, byte g, byte b, byte a) {}

  /** Before Valhalla: two references to two heap objects. */
  static class IdentityLine {
    IdentityPoint start;
    IdentityPoint end;

    IdentityLine(int i) {
      start = new IdentityPoint(i, i);
      end = new IdentityPoint(-i, -i);
    }
  }

  /** Value class fields, but nullable: 8 B + null marker does not fit 64 bits -> references. */
  static class Line {
    Point start;
    Point end;

    Line(int i) {
      start = new Point(i, i);
      end = new Point(-i, -i);
    }
  }

  /**
   * A record's components are strict (assigned before super(), never reassigned): no torn read is
   * possible, so the JVM inlines them without the 64-bit limit.
   */
  record RecordLine(Point start, Point end) {}

  /** Null-restricted strict fields: the JVM can inline them. */
  static class NullFreeLine {
    @NullRestricted final Point start;
    @NullRestricted final Point end;

    NullFreeLine(int i) {
      start = new Point(i, i);
      end = new Point(-i, -i);
      super();
    }
  }

  /** 4 B payload + null marker fits: flat even though nullable, and the Rgb inside is flat too. */
  static class Sprite {
    Pixel color;

    Sprite(int i) {
      color = Pixel.of(i);
    }
  }

  /** Every field is flattened on its own; five pixels = five 8-byte slots. */
  static class Sprite5 {
    Pixel p1, p2, p3, p4, p5;

    Sprite5(int i) {
      p1 = Pixel.of(i);
      p2 = Pixel.of(i + 1);
      p3 = Pixel.of(i + 2);
      p4 = Pixel.of(i + 3);
      p5 = Pixel.of(i + 4);
    }
  }

  /** Nothing of ours: LocalDate is 7 bytes, so even plain mutable fields hold it inline. */
  static class Period {
    LocalDate start;
    LocalDate end;
  }

  public static void main(String[] args) {
    Demo.shape(new Point(1, 2));
    Demo.shape(new IdentityLine(1));
    Demo.shape(new Line(1));
    Demo.shape(new RecordLine(new Point(1, 1), new Point(-1, -1)));
    Demo.shape(new NullFreeLine(1));
    Demo.shape(new Sprite(1));
    Demo.shape(new Sprite5(1));
    Demo.shape(new Period());

    Demo.section("arrays of " + N);
    var zero = new PackedPixel((byte) 0, (byte) 0, (byte) 0, (byte) 0);
    report("Pixel[]        nullable       ", new Pixel[N]);
    report("Pixel[]        null-restricted", Demo.newNullRestrictedArray(Pixel.class, N, Pixel.of(0)));
    report("PackedPixel[]  nullable       ", new PackedPixel[N]);
    report("PackedPixel[]  null-restricted", Demo.newNullRestrictedArray(PackedPixel.class, N, zero));
  }

  static void report(String name, Object[] array) {
    var size = Demo.sizeOf(array);
    System.out.printf(
        "%s flat: %s, %,d bytes, %d bytes/element%n", name, Demo.isFlatArray(array), size, (size - 16) / N);
  }
}
