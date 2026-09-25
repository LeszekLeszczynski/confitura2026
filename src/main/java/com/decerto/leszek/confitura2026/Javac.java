package com.decerto.leszek.confitura2026;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Compiles one class from a string with the running JDK's javac, preview enabled. */
public final class Javac {

  /** Compiler verdict; {@code loader} is null when compilation failed. */
  public record Result(boolean compiles, List<String> errors, ClassLoader loader) {}

  private Javac() {}

  public static Result compile(String className, String source) {
    try {
      var outDir = Files.createTempDirectory("javac");
      var diagnostics = new DiagnosticCollector<JavaFileObject>();
      var file = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return source;
        }
      };
      var options = List.of("--enable-preview", "-source", Runtime.version().feature() + "", "-proc:none", "-Xlint:none",
          "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED", "-d", outDir.toString());
      var compiles = ToolProvider.getSystemJavaCompiler().getTask(null, null, diagnostics, options, null, List.of(file)).call();
      var errors = diagnostics.getDiagnostics().stream()
          .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
          .map(d -> d.getMessage(null).lines().findFirst().orElse(""))
          .toList();
      var loader = compiles ? new URLClassLoader(new java.net.URL[] {outDir.toUri().toURL()}) : null;
      return new Result(compiles, errors, loader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
