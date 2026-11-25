package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newPoint;
import static com.onthegomap.planetiler.TestUtils.rectangle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.TileCoord;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openmaptiles.OpenMapTilesProfile;

class PoiTest extends AbstractLayerTest {

  private SourceFeature feature(boolean area, Map<String, Object> tags) {
    return area ? polygonFeature(tags) : pointFeature(tags);
  }

  @Test
  void testFenwayPark() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "stadium",
      "subclass", "stadium",
      "name", "Fenway Park",
      "rank", "<null>",
      "_minzoom", 14,
      "_labelgrid_size", 64d
    )), process(pointFeature(Map.of(
      "leisure", "stadium",
      "name", "Fenway Park"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testFunicularHalt(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "railway",
      "subclass", "halt",
      "rank", "<null>",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "railway", "station",
      "funicular", "yes",
      "name", "station"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testSubway(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "railway",
      "subclass", "subway",
      "rank", "<null>",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "railway", "station",
      "station", "subway",
      "name", "station"
    ))));
  }

  private List<FeatureCollector.Feature> testAggStops(List<SourceFeature> sourceFeatures) {
    sourceFeatures.forEach(this::process);

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, featureCollectorFactory, features::add);

    return features;
  }

  @Test
  void testAggStopJustOne() {
    var result = testAggStops(List.of(pointFeature(Map.of(
      "highway", "bus_stop",
      "name", "station",
      "uic_ref", "1"
    ))));
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "bus",
      "subclass", "bus_stop",
      "agg_stop", 1,
      "_minzoom", 14
    )), result);
  }

  @Test
  void testAggStopTwoWithSameSubclass() {
    var result = testAggStops(List.of(
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      ))
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", 1,
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      )
    ), result);
  }

  @Test
  void testAggStopThreeWithMixedSubclass() {
    var result = testAggStops(List.of(
      pointFeature(Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 3",
        "uic_ref", "1"
      ))
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 3",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", 1,
        "_minzoom", 14
      )
    ), result);
  }

  @Test
  void testAggStopThreeWithSameSubclass() {
    var result = testAggStops(List.of(
      SimpleFeature.create(newPoint(0, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 0),
      SimpleFeature.create(newPoint(1, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 1),
      SimpleFeature.create(newPoint(2, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 3",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 2)
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", 1,
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 3",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      )
    ), result);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testPlaceOfWorshipFromReligionTag(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "place_of_worship",
      "subclass", "religion value",
      "rank", "<null>",
      "_minzoom", area ? 13 : 14
    )), process(feature(area, Map.of(
      "amenity", "place_of_worship",
      "religion", "religion value",
      "name", "station"
    ))));
  }

  @Test
  void testPitchFromSportTag() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "pitch",
      "subclass", "soccer",
      "rank", "<null>"
    )), process(pointFeature(Map.of(
      "leisure", "pitch",
      "sport", "soccer",
      "name", "station"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testInformation(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "information",
      "subclass", "infotype",
      "layer", 3L,
      "level", 2L,
      "indoor", 1,
      "rank", "<null>"
    )), process(feature(area, Map.of(
      "tourism", "information",
      "information", "infotype",
      "name", "station",
      "layer", "3",
      "level", "2",
      "indoor", "yes"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testFerryTerminal(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "ferry_terminal",
      "subclass", "ferry_terminal",
      "name", "Water Taxi",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "amenity", "ferry_terminal",
      "information", "infotype",
      "name", "Water Taxi",
      "layer", "3",
      "level", "2",
      "indoor", "yes"
    ))));
  }

  @Test
  void testImportanceFromCategoryFameAndFootprint() {
    assertFeatures(14, List.of(Map.of("importance", 30)), process(pointFeature(Map.of(
      "amenity", "hospital",
      "name", "Clinic"
    ))));
    assertFeatures(14, List.of(Map.of("_layer", "landuse"), Map.of("_layer", "poi", "importance", 65)),
      process(polygonFeature(Map.of(
        "amenity", "hospital",
        "name", "Campus",
        "wikidata", "Q1"
      ))));
    assertFeatures(14, List.of(Map.of("importance", "<null>")), process(pointFeature(Map.of(
      "amenity", "hospital"
    ))));
  }

  @Test
  void testImportantPoisAppearAtLowZoom() {
    assertFeatures(14, List.of(Map.of("_layer", "landuse"), Map.of("_layer", "poi", "importance", 65, "_minzoom", 12)),
      process(polygonFeature(Map.of(
        "amenity", "hospital",
        "name", "Campus",
        "wikidata", "Q1"
      ))));
    assertFeatures(14, List.of(Map.of("importance", 45, "_minzoom", 13)), process(polygonFeature(Map.of(
      "tourism", "attraction",
      "name", "Plaza"
    ))));
    assertFeatures(14, List.of(Map.of("importance", 30, "_minzoom", 14)), process(pointFeature(Map.of(
      "amenity", "hospital",
      "name", "Clinic"
    ))));
    assertFeatures(14, List.of(Map.of("_minzoom", 12)), process(pointFeature(Map.of(
      "railway", "halt",
      "name", "Stop"
    ))));
    assertEquals(12, Poi.minzoomByImportance(Poi.Z12_IMPORTANCE));
    assertEquals(13, Poi.minzoomByImportance(Poi.Z12_IMPORTANCE - 1));
    assertEquals(13, Poi.minzoomByImportance(Poi.Z13_IMPORTANCE));
    assertEquals(14, Poi.minzoomByImportance(Poi.Z13_IMPORTANCE - 1));
    assertEquals(14, Poi.minzoomByImportance(null));
  }

  @Test
  void testGridRank() throws GeometryException {
    var layerName = Poi.LAYER_NAME;
    Assertions.assertEquals(List.of(), postProcess(13, List.of()));

    Assertions.assertEquals(List.of(pointFeature(
      layerName,
      Map.of("rank", 1),
      1
    )), postProcess(14, List.of(pointFeature(
      layerName,
      Map.of(),
      1
    ))));

    Assertions.assertEquals(List.of(
      pointFeature(
        layerName,
        Map.of("rank", 1, "name", "a"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 2, "name", "b"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 1, "name", "c"),
        2
      )
    ), postProcess(14, List.of(
      pointFeature(
        layerName,
        Map.of("name", "a"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "b"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "c"),
        2
      )
    )));
  }

  private List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    return postProcess(TileCoord.ofXYZ(0, 0, zoom), items);
  }

  private List<VectorTile.Feature> postProcess(TileCoord tile, List<VectorTile.Feature> items)
    throws GeometryException {
    var layers = new HashMap<String, List<VectorTile.Feature>>();
    layers.put(Poi.LAYER_NAME, items);
    return profile.postProcessTileFeatures(tile, layers).get(Poi.LAYER_NAME);
  }

  /** A hospital campus covering z2 tile 1/1, returning its POI's feature id. */
  private long processHospitalCampus() {
    var campus = SimpleFeature.create(GeoUtils.worldToLatLonCoords(rectangle(0.25, 0.5)), new HashMap<>(Map.of(
      "amenity", "hospital",
      "name", "General",
      "wikidata", "Q1"
    )), OpenMapTilesProfile.OSM_SOURCE, null, 7);
    for (var feature : process(campus)) {
      if (feature.getLayer().equals(Poi.LAYER_NAME)) {
        return feature.getId();
      }
    }
    return fail("no poi");
  }

  private static VectorTile.Feature poiAt(long id, double x, double y, String poiClass, String name, int importance) {
    return new VectorTile.Feature(Poi.LAYER_NAME, id, VectorTile.encodeGeometry(newPoint(x, y)),
      new HashMap<>(Map.of("class", poiClass, "name", name, Poi.IMPORTANCE, importance)), 1);
  }

  @Test
  void testContainedPoiIsDemotedAndReranked() throws GeometryException {
    processHospitalCampus();
    var tile = TileCoord.ofXYZ(1, 1, 2);
    var result = postProcess(tile, new ArrayList<>(List.of(
      poiAt(1, 128, 128, "pharmacy", "Pharmacy", 41),
      poiAt(2, 100, 100, "cafe", "Cafe", 30),
      poiAt(3, -10, 128, "pharmacy", "Outside", 25)
    )));
    assertEquals(List.of("Cafe", "Outside", "Pharmacy"), result.stream().map(f -> f.tags().get("name")).toList());
    assertEquals(List.of(30, 25, 20), result.stream().map(f -> f.tags().get(Poi.IMPORTANCE)).toList());
    assertEquals(List.of(1, 2, 3), result.stream().map(f -> f.tags().get("rank")).toList());
  }

  @Test
  void testPoiIsNotDemotedByItsOwnPolygon() throws GeometryException {
    long id = processHospitalCampus();
    var result = postProcess(TileCoord.ofXYZ(1, 1, 2), new ArrayList<>(List.of(
      poiAt(id, 128, 128, "hospital", "General", 65),
      poiAt(id + 1, 128, 128, "hospital", "Wing", 65)
    )));
    assertEquals(List.of(65, 65), result.stream().map(f -> f.tags().get(Poi.IMPORTANCE)).toList());
  }

  private List<FeatureCollector.Feature> poisOf(Map<String, Object> tags) {
    var result = new ArrayList<FeatureCollector.Feature>();
    for (var feature : process(polygonFeature(tags))) {
      if (feature.getLayer().equals(Poi.LAYER_NAME)) {
        result.add(feature);
      }
    }
    return result;
  }

  @Test
  void testNamedBuildingIsLabelled() {
    var pois = poisOf(Map.of("building", "school", "name", "Math Building"));
    assertEquals(1, pois.size());
    var attrs = pois.getFirst().getAttrsAtZoom(14);
    assertEquals("building", attrs.get("class"));
    assertEquals("school", attrs.get("subclass"));
    assertEquals("Math Building", attrs.get("name"));
    assertEquals(null, attrs.get(Poi.IMPORTANCE));
    assertEquals(Poi.BUILDING_MINZOOM, pois.getFirst().getMinZoom());
  }

  @Test
  void testUnnamedBuildingIsNotLabelled() {
    assertEquals(List.of(), poisOf(Map.of("building", "school")));
  }

  @Test
  void testBuildingThatIsAPoiIsLabelledOnce() {
    var pois = poisOf(Map.of("building", "school", "amenity", "theatre", "name", "A Building"));
    assertEquals(List.of("theatre"), pois.stream().map(f -> f.getAttrsAtZoom(14).get("class")).toList());
  }

  @Test
  void testBuildingPartIsNotLabelled() {
    assertEquals(List.of(), poisOf(Map.of("building:part", "yes", "name", "Tower")));
  }

  @Test
  void testEmbassy() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "office",
      "subclass", "diplomatic",
      "name", "The Embassy"
    )), process(pointFeature(Map.of(
      "office", "diplomatic",
      "name", "The Embassy"
    ))));
  }

  @Test
  void testLocksmith() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "shop",
      "subclass", "locksmith",
      "name", "The Locksmith"
    )), process(pointFeature(Map.of(
      "shop", "locksmith",
      "name", "The Locksmith"
    ))));
  }

  @Test
  void testAtm() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "atm",
      "subclass", "atm",
      "name", "ATM name"
    ));
    // prefer name, otherwise fall back to operator, or else network
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "name", "ATM name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "name", "ATM name",
      "operator", "ATM operator",
      "network", "ATM network"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "operator", "ATM name",
      "network", "ATM network"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "network", "ATM name"
    ))));
  }

  @Test
  void testParcelLocker() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "post",
      "subclass", "parcel_locker",
      "name", "Parcel Locker name"
    ));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "brand", "Parcel Locker name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "operator", "Parcel Locker name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "operator", "Parcel Locker",
      "ref", "name"
    ))));
  }

  @Test
  void testParcelLockerCornerCase() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "post",
      "subclass", "parcel_locker",
      "name", "Corner Case"
    ));
    // no brand, no operator, just ref
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "ref", "Corner Case"
    ))));
  }

  @Test
  void testChargingStation() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "fuel",
      "subclass", "charging_station",
      "name", "Some Charging Station Operator"
    ));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "brand", "Some Charging Station Operator"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "operator", "Some Charging Station Operator"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "operator", "Some Charging Station",
      "ref", "Operator"
    ))));
  }

}
