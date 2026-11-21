package org.openmaptiles.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PoiImportanceTest {

  private static final int HOSPITAL = 20;
  private static final int PARK = 120;
  private static final int BAR = 800;

  @TempDir
  Path dir;

  private PoiImportance qrank(String csv) throws IOException {
    Path file = dir.resolve("qrank.csv");
    Files.writeString(file, csv);
    return PoiImportance.fromQrank(file);
  }

  @Test
  void categorySpansTopToUnranked() {
    assertEquals(PoiImportance.CATEGORY_MAX, PoiImportance.category(HOSPITAL));
    assertEquals(0, PoiImportance.category(1_000));
    assertTrue(PoiImportance.category(PARK) > PoiImportance.category(BAR));
  }

  @Test
  void footprintGrowsPerDecadeAndCaps() {
    assertEquals(0, PoiImportance.footprint(0));
    assertEquals(0, PoiImportance.footprint(500));
    assertEquals(6, PoiImportance.footprint(10_000));
    assertEquals(12, PoiImportance.footprint(100_000));
    assertEquals(PoiImportance.SIZE_MAX, PoiImportance.footprint(1e12));
  }

  @Test
  void readsWikidataNumbers() {
    assertEquals(42, PoiImportance.wikidataNumber("Q42"));
    assertEquals(7, PoiImportance.wikidataNumber("Q7;Q8"));
    assertEquals(-1, PoiImportance.wikidataNumber("42"));
    assertEquals(-1, PoiImportance.wikidataNumber("Q"));
    assertEquals(-1, PoiImportance.wikidataNumber("Q4x"));
    assertEquals(-1, PoiImportance.wikidataNumber(null));
  }

  @Test
  void loadsQrankSkippingHeaderAndObscureItems() throws IOException {
    var importance = qrank("Entity,QRank\nQ1,100000000\nQ2,999\nQ3,100000\n");
    assertEquals(2, importance.size());
    assertEquals(PoiImportance.FAME_LINKED + PoiImportance.FAME_VIEWS_MAX, importance.fame("Q1", false));
    assertEquals(PoiImportance.FAME_LINKED, importance.fame("Q2", false));
    assertEquals(PoiImportance.FAME_LINKED + 12, importance.fame("Q3", false));
    assertEquals(0, importance.fame(null, false));
    assertEquals(PoiImportance.FAME_LINKED, importance.fame(null, true));
  }

  @Test
  void flagshipHospitalOutranksItsNeighbour() throws IOException {
    var importance = qrank("Entity,QRank\nQ1,400000\nQ2,30000\n");
    int flagship = importance.score(HOSPITAL, "Q1", true, 150_000);
    int neighbour = importance.score(HOSPITAL, "Q2", true, 12_000);
    assertTrue(flagship > neighbour, flagship + " > " + neighbour);
  }

  @Test
  void famousParkOutranksPocketParks() throws IOException {
    var importance = qrank("Entity,QRank\nQ1,300000\n");
    int common = importance.score(PARK, "Q1", true, 200_000);
    int pocket = importance.score(PARK, null, false, 2_000);
    assertTrue(common > pocket + 20, common + " vs " + pocket);
  }

  @Test
  void neverExceedsMax() {
    var importance = PoiImportance.withoutPageviews();
    assertTrue(importance.score(HOSPITAL, "Q1", true, 1e12) <= PoiImportance.MAX);
  }
}
