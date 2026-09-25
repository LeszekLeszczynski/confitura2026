package com.decerto.leszek.confitura2026;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.decerto.leszek.confitura2026.demo05_speed.FlatArraySpeed;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Running JMH in a unit test takes minutes; instead check the benchmark was generated at all. */
class FlatArraySpeedTest {

  @Test
  void benchmarkIsRegisteredWithJmh() throws IOException {
    try (var in = getClass().getResourceAsStream("/META-INF/BenchmarkList")) {
      assertNotNull(in, "JMH annotation processor did not run");
      var benchmarks = new String(in.readAllBytes(), UTF_8);
      assertTrue(benchmarks.contains(FlatArraySpeed.class.getName()));
      Stream.of("sumFlat", "sumRefs", "sumIdentity")
          .forEach(method -> assertTrue(benchmarks.contains(method), method + " not registered"));
    }
  }
}
