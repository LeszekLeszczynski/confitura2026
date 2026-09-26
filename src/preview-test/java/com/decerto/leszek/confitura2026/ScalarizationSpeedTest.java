package com.decerto.leszek.confitura2026;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.decerto.leszek.confitura2026.demo24_practice.ScalarizationSpeed;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Running JMH here would take minutes; check the benchmarks were generated instead. */
class ScalarizationSpeedTest {

  @Test
  void benchmarksAreRegisteredWithJmh() throws IOException {
    try (var in = getClass().getResourceAsStream("/META-INF/BenchmarkList")) {
      assertNotNull(in, "JMH annotation processor did not run");
      var benchmarks = new String(in.readAllBytes(), UTF_8);
      assertTrue(benchmarks.contains(ScalarizationSpeed.class.getName()));
      Stream.of("pricingValue", "pricingIdentity", "pricingRawLong")
          .forEach(method -> assertTrue(benchmarks.contains(method), method + " not registered"));
    }
  }
}
