package com.decerto.leszek.confitura2026.demo20_article;

import static com.decerto.leszek.confitura2026.demo20_article.ArticleModels.SECTIONS;

import com.decerto.leszek.confitura2026.Demo;
import com.decerto.leszek.confitura2026.demo20_article.ArticleModels.Strings;
import java.util.ArrayList;
import java.util.List;

/**
 * Model C for the article: Author and Section as value records. Author flattens into the article
 * (a strict record component), but it is four references either way; the sections sit in a List,
 * so they are buffered objects either way - and every byte that matters is in the Strings.
 *
 * <pre>
 * ./run.sh -p ValueArticleModels
 * </pre>
 */
public class ValueArticleModels {

  value record VAuthor(String name, String email, String bio, String avatarUrl) {}

  value record VSection(String heading, String body, String anchor) {}

  record VArticle(String slug, String title, String summary, VAuthor author, List<String> tags, List<VSection> sections) {}

  static VArticle values(Strings s) {
    var sections = new ArrayList<VSection>(SECTIONS);
    for (var k = 0; k < SECTIONS; k++) {
      sections.add(new VSection(s.headings().get(k), s.bodies().get(k), s.anchors().get(k)));
    }
    return new VArticle(s.slug(), s.title(), s.summary(), new VAuthor(s.name(), s.email(), s.bio(), s.avatarUrl()), new ArrayList<>(s.tags()), sections);
  }

  public static void main(String[] args) {
    Demo.section("shapes");
    Demo.shape(ArticleModels.classic(Strings.of(0)));
    Demo.shape(ArticleModels.records(Strings.of(0)));
    Demo.shape(values(Strings.of(0)));
    Demo.shape(values(Strings.of(0)).sections().get(0));

    ArticleModels.header();
    ArticleModels.measure("A classic classes", ArticleModels::classic);
    ArticleModels.measure("B records", ArticleModels::records);
    ArticleModels.measure("C records + value records", ValueArticleModels::values);
  }
}
