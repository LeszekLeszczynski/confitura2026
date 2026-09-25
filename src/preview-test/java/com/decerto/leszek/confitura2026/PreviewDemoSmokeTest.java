package com.decerto.leszek.confitura2026;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.decerto.leszek.confitura2026.demo04_flattening.ArrayFlattening;
import com.decerto.leszek.confitura2026.demo06_lists.ValueObjectsInList;
import com.decerto.leszek.confitura2026.demo07b_nullmarker.NullMarker;
import com.decerto.leszek.confitura2026.demo08_fields.FieldFlattening;
import com.decerto.leszek.confitura2026.demo10_frameworks.FrameworkInstantiation;
import com.decerto.leszek.confitura2026.demo12_bypass.DefaultValueBypass;
import com.decerto.leszek.confitura2026.demo14_identity_breaks.IdentityAssumptions;
import com.decerto.leszek.confitura2026.demo18_restrictions.ValueClassRestrictions;
import com.decerto.leszek.confitura2026.demo19_invoice.ValueInvoiceModels;
import com.decerto.leszek.confitura2026.demo20_article.ValueArticleModels;
import com.decerto.leszek.confitura2026.demo21_json.JacksonInvoice;
import com.decerto.leszek.confitura2026.demo22_period_line.ValuePeriodVsLine;
import com.decerto.leszek.confitura2026.demo23_scalarization.Scalarization;
import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationInPractice;
import com.decerto.leszek.confitura2026.demo25_warmup.WarmUpCost;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Demos under src/preview/java only exist when built with -Ppreview. Add new ones to {@link #demos()}. */
class PreviewDemoSmokeTest {

  static Stream<Class<?>> demos() {
    return Stream.of(
        ArrayFlattening.class,
        NullMarker.class,
        ValueObjectsInList.class,
        FieldFlattening.class,
        FrameworkInstantiation.class,
        DefaultValueBypass.class,
        IdentityAssumptions.class,
        ValueClassRestrictions.class,
        ValueInvoiceModels.class,
        ValueArticleModels.class,
        JacksonInvoice.class,
        ValuePeriodVsLine.class,
        Scalarization.class,
        ScalarizationInPractice.class,
        WarmUpCost.class);
  }

  @ParameterizedTest
  @MethodSource("demos")
  void demoRuns(Class<?> demo) {
    assertDoesNotThrow(
        () -> demo.getMethod("main", String[].class).invoke(null, (Object) new String[0]));
  }
}
