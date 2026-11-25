package org.openmaptiles.util;

import com.carrotsearch.hppc.LongIntMap;
import com.onthegomap.planetiler.collection.Hppc;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * How prominent a POI is, from 0 to {@link #MAX}: its category, its fame (Wikidata and QRank pageviews) and the size of
 * its footprint.
 */
public final class PoiImportance {

  public static final int MAX = 100;

  static final int CATEGORY_MAX = 30;
  static final int FAME_LINKED = 10;
  static final int FAME_VIEWS_MAX = 30;
  static final int SIZE_MAX = 25;

  /** Yearly pageviews below this add nothing, so they are not worth holding in memory. */
  static final int MIN_VIEWS = 1_000;

  private static final int TOP_CLASS_RANK = 20;
  private static final int DEFAULT_CLASS_RANK = 1_000;
  private static final double MIN_SQUARE_METERS = 1_000;

  private final LongIntMap views;

  private PoiImportance(LongIntMap views) {
    this.views = views;
  }

  public static PoiImportance withoutPageviews() {
    return new PoiImportance(Hppc.newLongIntHashMap());
  }

  /** Reads a QRank CSV ({@code Entity,QRank}, optionally gzipped), keeping items with enough views to score. */
  public static PoiImportance fromQrank(Path path) throws IOException {
    LongIntMap views = Hppc.newLongIntHashMap();
    try (
      InputStream raw = Files.newInputStream(path);
      InputStream in = path.toString().endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
      var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)
    ) {
      String line;
      while ((line = reader.readLine()) != null) {
        int comma = line.indexOf(',');
        long id = comma > 0 ? wikidataNumber(line.substring(0, comma)) : -1;
        if (id < 0) {
          continue;
        }
        long count = Long.parseLong(line, comma + 1, line.length(), 10);
        if (count >= MIN_VIEWS) {
          views.put(id, (int) Math.min(count, Integer.MAX_VALUE));
        }
      }
    }
    return new PoiImportance(views);
  }

  public int size() {
    return views.size();
  }

  public int score(int classRank, String wikidata, boolean hasWikipedia, double squareMeters) {
    return category(classRank) + fame(wikidata, hasWikipedia) + footprint(squareMeters);
  }

  /** Maps OpenMapTiles' class ranks (20 for a hospital up to 1000 for an unranked class) onto a log scale. */
  static int category(int classRank) {
    double t = Math.log((double) classRank / TOP_CLASS_RANK) / Math.log((double) DEFAULT_CLASS_RANK / TOP_CLASS_RANK);
    return (int) Math.round(CATEGORY_MAX * (1 - Math.clamp(t, 0, 1)));
  }

  int fame(String wikidata, boolean hasWikipedia) {
    long id = wikidataNumber(wikidata);
    if (id < 0 && !hasWikipedia) {
      return 0;
    }
    int count = id < 0 ? 0 : views.getOrDefault(id, 0);
    return FAME_LINKED + perDecade(count, MIN_VIEWS, 6, FAME_VIEWS_MAX);
  }

  public static int footprint(double squareMeters) {
    return perDecade(squareMeters, MIN_SQUARE_METERS, 6, SIZE_MAX);
  }

  private static int perDecade(double value, double floor, double points, int max) {
    if (value <= floor) {
      return 0;
    }
    return (int) Math.min(max, Math.round(points * Math.log10(value / floor)));
  }

  /** The number in a Wikidata id ({@code Q42} → 42), or -1. Lists such as {@code Q1;Q2} use the first. */
  static long wikidataNumber(String id) {
    if (id == null || id.length() < 2 || (id.charAt(0) != 'Q' && id.charAt(0) != 'q')) {
      return -1;
    }
    long n = 0;
    for (int i = 1; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c < '0' || c > '9') {
        return i > 1 && c == ';' ? n : -1;
      }
      n = n * 10 + (c - '0');
    }
    return n;
  }
}
