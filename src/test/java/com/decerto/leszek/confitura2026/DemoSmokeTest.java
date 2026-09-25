package com.decerto.leszek.confitura2026;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.decerto.leszek.confitura2026.demo01_headers.ObjectHeader;
import com.decerto.leszek.confitura2026.demo02_table.ObjectTable;
import com.decerto.leszek.confitura2026.demo03_epsilon.EpsilonAllocation;
import com.decerto.leszek.confitura2026.demo07_covariance.CovariantIntegerArray;
import com.decerto.leszek.confitura2026.demo09_identity.IdentityOperations;
import com.decerto.leszek.confitura2026.demo11_entity.ValueFieldsInEntity;
import com.decerto.leszek.confitura2026.demo13_identitymap.IdentityMapBreakage;
import com.decerto.leszek.confitura2026.demo15_commons.CommonsLangToString;
import com.decerto.leszek.confitura2026.demo16_sizeof.CacheSizing;
import com.decerto.leszek.confitura2026.demo17_optional.OptionalValueClass;
import com.decerto.leszek.confitura2026.demo19_invoice.InvoiceModels;
import com.decerto.leszek.confitura2026.demo20_article.ArticleModels;
import com.decerto.leszek.confitura2026.demo22_period_line.PeriodVsLine;
import com.decerto.leszek.confitura2026.demo24_practice.DateChain;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every demo must at least run on the current JDK. Add new demos to {@link #demos()}. */
class DemoSmokeTest {

  static Stream<Class<?>> demos() {
    return Stream.of(
        ObjectHeader.class,
        ObjectTable.class,
        EpsilonAllocation.class,
        CovariantIntegerArray.class,
        IdentityOperations.class,
        ValueFieldsInEntity.class,
        IdentityMapBreakage.class,
        CommonsLangToString.class,
        CacheSizing.class,
        OptionalValueClass.class,
        InvoiceModels.class,
        ArticleModels.class,
        PeriodVsLine.class,
        DateChain.class);
  }

  @ParameterizedTest
  @MethodSource("demos")
  void demoRuns(Class<?> demo) {
    assertDoesNotThrow(
        () -> demo.getMethod("main", String[].class).invoke(null, (Object) new String[0]));
  }
}
