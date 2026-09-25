package com.decerto.leszek.confitura2026.demo22_period_line;

import com.decerto.leszek.confitura2026.Demo;
import com.decerto.leszek.confitura2026.demo22_period_line.PeriodVsLine.IdentityLine;
import com.decerto.leszek.confitura2026.demo22_period_line.PeriodVsLine.Period;
import java.time.LocalDate;

/**
 * The same holder, three ways: LocalDate (a 7-byte value class the JDK migrated for us), our own
 * 8-byte value class, and the identity class it replaced. Only one of the three is inlined - and it
 * is not the one we wrote.
 *
 * <pre>
 * ./run.sh -p ValuePeriodVsLine
 * ./run.sh -p ValuePeriodVsLine -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout
 * </pre>
 */
public class ValuePeriodVsLine {

  /** Exactly the class from the question: two ints, no identity. Fields are implicitly final. */
  public static value class Point {
    int x;
    int y;

    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public String toString() {
      return "Point[x=" + x + ", y=" + y + "]";
    }
  }

  /** Ordinary class, ordinary reassignable fields - the same shape as Period. */
  public static class Line {
    Point start;
    Point end;

    Line(int i) {
      start = new Point(i, i);
      end = new Point(-i, -i);
    }
  }

  /** The same two points as record components: strict fields, so the 64-bit limit does not apply. */
  record RecordLine(Point start, Point end) {}

  public static void main(String[] args) {
    System.out.printf("LocalDate is a value class: %s, our Point: %s, IdentityPoint: %s%n",
        LocalDate.class.isValue(), Point.class.isValue(), PeriodVsLine.IdentityPoint.class.isValue());

    PeriodVsLine.header();
    PeriodVsLine.report("Period      (2 x LocalDate)", new Period(1), "start", "end");
    PeriodVsLine.report("Line        (2 x value Point)", new Line(1), "start", "end");
    PeriodVsLine.report("IdentityLine(2 x class Point)", new IdentityLine(1), "start", "end");
    PeriodVsLine.report("RecordLine  (2 x value Point)", new RecordLine(new Point(1, 1), new Point(-1, -1)), "start", "end");

    Demo.section("the pieces themselves");
    System.out.printf("  LocalDate               %2d B  (7 bytes of payload: int year, byte month, byte day)%n", Demo.sizeOf(LocalDate.of(2026, 9, 25)));
    System.out.printf("  value Point, buffered   %2d B  (8 bytes of payload + null marker -> 17 -> 24)%n", Demo.sizeOf(new Point(1, 2)));
    System.out.printf("  identity Point          %2d B  (8 bytes of payload, no marker)%n", Demo.sizeOf(new PeriodVsLine.IdentityPoint(1, 2)));

    Demo.shape(new Period(1));
    Demo.shape(new Line(1));
    Demo.shape(new IdentityLine(1));
    Demo.shape(new RecordLine(new Point(1, 1), new Point(-1, -1)));
  }
}
