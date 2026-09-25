package com.decerto.leszek.confitura2026.demo20_article;

import com.decerto.leszek.confitura2026.Demo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * The counter-example to the invoice: a model that is almost entirely Strings. An article with an
 * author, tags and sections, every string freshly allocated per article (as content would be). A:
 * mutable classes, B: records; ValueArticleModels adds C with value records. Value classes cannot
 * shrink what sits behind a reference, so the interesting number is how little changes.
 *
 * <pre>
 * ./run.sh    ArticleModels
 * ./run.sh -p ArticleModels
 * </pre>
 */
public class ArticleModels {

  public static final int ARTICLES = 10_000;
  public static final int SECTIONS = 5;
  public static final int TAGS = 4;
  static final String LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. ";

  /** All the strings of one article, freshly allocated - identical for every model. */
  public record Strings(String slug, String title, String summary, String name, String email, String bio, String avatarUrl,
      List<String> tags, List<String> headings, List<String> bodies, List<String> anchors) {

    public static Strings of(int i) {
      return new Strings("article-" + i, "Article number " + i, "A short summary of article " + i + ".",
          "Author " + i, "author" + i + "@example.com", "Bio of author " + i + ". " + LOREM, "https://cdn.example.com/avatars/" + i + ".png",
          IntStream.range(0, TAGS).mapToObj(t -> "tag-" + i + "-" + t).toList(),
          IntStream.range(0, SECTIONS).mapToObj(s -> "Section " + s + " of " + i).toList(),
          IntStream.range(0, SECTIONS).mapToObj(s -> LOREM.repeat(2) + i + "/" + s).toList(),
          IntStream.range(0, SECTIONS).mapToObj(s -> "s" + s + "-" + i).toList());
    }

    /** Bytes of the String objects and their byte[] backing arrays - the part no model can change. */
    public long bytes() {
      var all = new ArrayList<String>(List.of(slug, title, summary, name, email, bio, avatarUrl));
      all.addAll(tags);
      all.addAll(headings);
      all.addAll(bodies);
      all.addAll(anchors);
      return all.stream().mapToLong(s -> Demo.sizeOf(s) + Demo.sizeOf(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))).sum();
    }
  }

  // ---- A: classic mutable classes -------------------------------------------------------------

  public static class ClassicAuthor {
    String name;
    String email;
    String bio;
    String avatarUrl;
  }

  public static class ClassicSection {
    String heading;
    String body;
    String anchor;
  }

  public static class ClassicArticle {
    String slug;
    String title;
    String summary;
    ClassicAuthor author;
    List<String> tags;
    List<ClassicSection> sections;
  }

  public static ClassicArticle classic(Strings s) {
    var article = new ClassicArticle();
    article.slug = s.slug();
    article.title = s.title();
    article.summary = s.summary();
    article.author = new ClassicAuthor();
    article.author.name = s.name();
    article.author.email = s.email();
    article.author.bio = s.bio();
    article.author.avatarUrl = s.avatarUrl();
    article.tags = new ArrayList<>(s.tags());
    article.sections = new ArrayList<>(SECTIONS);
    for (var k = 0; k < SECTIONS; k++) {
      var section = new ClassicSection();
      section.heading = s.headings().get(k);
      section.body = s.bodies().get(k);
      section.anchor = s.anchors().get(k);
      article.sections.add(section);
    }
    return article;
  }

  // ---- B: records ------------------------------------------------------------------------------

  public record RAuthor(String name, String email, String bio, String avatarUrl) {}

  public record RSection(String heading, String body, String anchor) {}

  public record RArticle(String slug, String title, String summary, RAuthor author, List<String> tags, List<RSection> sections) {}

  public static RArticle records(Strings s) {
    var sections = new ArrayList<RSection>(SECTIONS);
    for (var k = 0; k < SECTIONS; k++) {
      sections.add(new RSection(s.headings().get(k), s.bodies().get(k), s.anchors().get(k)));
    }
    return new RArticle(s.slug(), s.title(), s.summary(), new RAuthor(s.name(), s.email(), s.bio(), s.avatarUrl()), new ArrayList<>(s.tags()), sections);
  }

  // ---- measurement -----------------------------------------------------------------------------

  public static void main(String[] args) {
    Demo.section("shapes");
    Demo.shape(classic(Strings.of(0)));
    Demo.shape(records(Strings.of(0)));

    header();
    measure("A classic classes", ArticleModels::classic);
    measure("B records", ArticleModels::records);
  }

  public static void header() {
    Demo.section(ARTICLES + " articles, " + SECTIONS + " sections and " + TAGS + " tags each");
    System.out.printf("  strings per article (String objects + byte[] backing): %,d bytes - identical for every model%n%n", Strings.of(0).bytes());
    System.out.printf("  %-28s %12s %12s %8s%n", "model", "structure", "total", "strings");
    System.out.printf("  %-28s %12s %12s %8s%n", "", "per article", "per article", "share");
  }

  /** Builds the model on pre-made strings, so only the model's own objects are counted. */
  public static <T> void measure(String name, Function<Strings, T> build) {
    var strings = IntStream.range(0, ARTICLES).mapToObj(Strings::of).toList();
    for (var round = 0; round < 3; round++) {
      strings.forEach(build::apply); // warm-up
    }
    var before = Demo.allocatedBytes();
    var articles = new ArrayList<T>(ARTICLES);
    for (var s : strings) {
      articles.add(build.apply(s));
    }
    var structure = (Demo.allocatedBytes() - before) / ARTICLES;
    var stringBytes = Strings.of(0).bytes();
    System.out.printf("  %-28s %,12d %,12d %7.0f%%%n", name, structure, structure + stringBytes, 100.0 * stringBytes / (structure + stringBytes));
  }
}
