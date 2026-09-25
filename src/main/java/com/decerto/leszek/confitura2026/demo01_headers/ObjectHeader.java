package com.decerto.leszek.confitura2026.demo01_headers;

import com.decerto.leszek.confitura2026.Demo;

/**
 * What does an empty object cost? JDK 28 uses compact object headers by default (8 bytes). Run
 * with -XX:-UseCompactObjectHeaders to see the classic mark word + class pointer (12 bytes).
 */
public class ObjectHeader {

  static class Empty {}

  static class OneInt {
    int value;
  }

  public static void main(String[] args) {
    Demo.vm();
    Demo.layout(Object.class);
    Demo.layout(Empty.class);
    Demo.layout(OneInt.class);
  }
}
