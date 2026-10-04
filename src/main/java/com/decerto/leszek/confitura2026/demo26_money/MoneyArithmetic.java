package com.decerto.leszek.confitura2026.demo26_money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The same premium calculation - base amount, a loading percentage, then tax - written three ways here,
 * plus two value-class versions in ValueMoneyArithmetic. Every variant must produce the same answer to the cent; the benchmark asserts it before measuring.
 *
 * <p>Amounts are held in cents. A premium will not exceed 92 quadrillion cents, so a long is not a
 * compromise here - reaching for BigDecimal is about rounding on division, not about range.
 */
public final class MoneyArithmetic {

  private MoneyArithmetic() {}

  public static final int TAX_PERCENT = 23;

  /** HALF_UP on positive cents, without leaving integer arithmetic. */
  static long percentOf(long cents, int percent) {
    return (cents * percent + 50) / 100;
  }

  // ---- 1. no domain type at all ---------------------------------------------------------------

  public static long premiumRaw(long[] baseCents, int[] loadings) {
    var total = 0L;
    for (var i = 0; i < baseCents.length; i++) {
      var net = percentOf(baseCents[i], loadings[i]);
      total += percentOf(net, 100 + TAX_PERCENT);
    }
    return total;
  }

  // ---- 2. a domain type wrapping BigDecimal (the reflex) --------------------------------------

  public static final class BigMoney {
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final BigMoney ZERO = new BigMoney(BigDecimal.ZERO.setScale(2));

    final BigDecimal amount;

    public BigMoney(BigDecimal amount) {
      if (amount.signum() < 0) {
        throw new IllegalArgumentException("negative amount: " + amount);
      }
      this.amount = amount;
    }

    public BigMoney percent(int percent) {
      return new BigMoney(amount.multiply(BigDecimal.valueOf(percent))
          .divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    public BigMoney plus(BigMoney other) {
      return new BigMoney(amount.add(other.amount));
    }

    public long cents() {
      return amount.movePointRight(2).longValueExact();
    }
  }

  public static long premiumBigDecimal(BigMoney[] bases, int[] loadings) {
    var total = BigMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      var net = bases[i].percent(loadings[i]);
      total = total.plus(net.percent(100 + TAX_PERCENT));
    }
    return total.cents();
  }

  // ---- 3. a domain type wrapping long cents, as an ordinary record -----------------------------

  public record LongMoney(long cents) {
    public static final LongMoney ZERO = new LongMoney(0);

    public LongMoney {
      if (cents < 0) {
        throw new IllegalArgumentException("negative amount: " + cents);
      }
    }

    public LongMoney percent(int percent) {
      return new LongMoney(percentOf(cents, percent));
    }

    public LongMoney plus(LongMoney other) {
      return new LongMoney(cents + other.cents);
    }
  }

  public static long premiumLongMoney(LongMoney[] bases, int[] loadings) {
    var total = LongMoney.ZERO;
    for (var i = 0; i < bases.length; i++) {
      var net = bases[i].percent(loadings[i]);
      total = total.plus(net.percent(100 + TAX_PERCENT));
    }
    return total.cents();
  }
}
