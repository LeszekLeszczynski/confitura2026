package com.decerto.leszek.confitura2026.demo26_money;

import static com.decerto.leszek.confitura2026.demo26_money.MoneyArithmetic.TAX_PERCENT;
import static com.decerto.leszek.confitura2026.demo26_money.MoneyArithmetic.percentOf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The same two domain types as value classes: one over long cents, one over BigDecimal. */
public final class ValueMoneyArithmetic {

  private ValueMoneyArithmetic() {}

  // ---- 4. value record over long cents ----------------------------------------------------------

  public static value record ValueMoney(long cents) {
    public static final ValueMoney ZERO = new ValueMoney(0);

    public ValueMoney {
      if (cents < 0) {
        throw new IllegalArgumentException("negative amount: " + cents);
      }
    }

    public ValueMoney percent(int percent) {
      return new ValueMoney(percentOf(cents, percent));
    }

    public ValueMoney plus(ValueMoney other) {
      return new ValueMoney(cents + other.cents);
    }
  }

  public static long premiumValueMoney(ValueMoney[] bases, int[] loadings) {
    var total = ValueMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      var net = bases[i].percent(loadings[i]);
      total = total.plus(net.percent(100 + TAX_PERCENT));
    }
    return total.cents();
  }

  // ---- diagnostic: the exact shape of demo 24 - two operations, no rounding, no validation ------

  public static value record PlainMoney(long cents) {
    public static final PlainMoney ZERO = new PlainMoney(0);

    public PlainMoney times(int percent) {
      return new PlainMoney(cents * percent / 100);
    }

    public PlainMoney plus(PlainMoney other) {
      return new PlainMoney(cents + other.cents);
    }
  }

  public static long premiumPlainMoney(PlainMoney[] bases, int[] loadings) {
    var total = PlainMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      total = total.plus(bases[i].times(loadings[i]));
    }
    return total.cents();
  }

  /** Diagnostic: the real calculation (3 operations + rounding) but without the invariant check. */
  public static value record UncheckedMoney(long cents) {
    public static final UncheckedMoney ZERO = new UncheckedMoney(0);

    public UncheckedMoney percent(int percent) {
      return new UncheckedMoney(percentOf(cents, percent));
    }

    public UncheckedMoney plus(UncheckedMoney other) {
      return new UncheckedMoney(cents + other.cents);
    }
  }

  public static long premiumUncheckedMoney(UncheckedMoney[] bases, int[] loadings) {
    var total = UncheckedMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      var net = bases[i].percent(loadings[i]);
      total = total.plus(net.percent(100 + TAX_PERCENT));
    }
    return total.cents();
  }

  // ---- 5. value record over BigDecimal: does value-ness help when a reference is inside? --------

  public static value record ValueBigMoney(BigDecimal amount) {
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final ValueBigMoney ZERO = new ValueBigMoney(BigDecimal.ZERO.setScale(2));

    public ValueBigMoney percent(int percent) {
      return new ValueBigMoney(amount.multiply(BigDecimal.valueOf(percent))
          .divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    public ValueBigMoney plus(ValueBigMoney other) {
      return new ValueBigMoney(amount.add(other.amount));
    }

    public long cents() {
      return amount.movePointRight(2).longValueExact();
    }
  }

  public static long premiumValueBigMoney(ValueBigMoney[] bases, int[] loadings) {
    var total = ValueBigMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      var net = bases[i].percent(loadings[i]);
      total = total.plus(net.percent(100 + TAX_PERCENT));
    }
    return total.cents();
  }
}
