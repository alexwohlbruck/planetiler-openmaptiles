package org.openmaptiles.util;

import com.onthegomap.planetiler.geo.GeoUtils;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demotes POIs that sit inside a larger POI of the same family: a pharmacy inside a hospital, a shop inside a mall, a
 * library inside a university campus.
 * <p>
 * Containers are collected while features are processed and indexed the first time a tile asks, which is after every
 * feature has been processed.
 */
public final class PoiContainment {

  private static final Logger LOGGER = LoggerFactory.getLogger(PoiContainment.class);

  private enum Family {
    HEALTH,
    RETAIL,
    EDUCATION
  }

  private static final Map<String, Family> FAMILIES = Map.ofEntries(
    Map.entry("hospital", Family.HEALTH),
    Map.entry("pharmacy", Family.HEALTH),
    Map.entry("doctors", Family.HEALTH),
    Map.entry("dentist", Family.HEALTH),
    Map.entry("shop", Family.RETAIL),
    Map.entry("grocery", Family.RETAIL),
    Map.entry("clothing_store", Family.RETAIL),
    Map.entry("alcohol_shop", Family.RETAIL),
    Map.entry("music", Family.RETAIL),
    Map.entry("bakery", Family.RETAIL),
    Map.entry("hairdresser", Family.RETAIL),
    Map.entry("fast_food", Family.RETAIL),
    Map.entry("restaurant", Family.RETAIL),
    Map.entry("cafe", Family.RETAIL),
    Map.entry("bar", Family.RETAIL),
    Map.entry("beer", Family.RETAIL),
    Map.entry("ice_cream", Family.RETAIL),
    Map.entry("college", Family.EDUCATION),
    Map.entry("school", Family.EDUCATION),
    Map.entry("library", Family.EDUCATION)
  );
  private static final Set<String> CONTAINER_SUBCLASSES = Set.of("hospital", "mall", "university", "college");

  private record Container(long id, PreparedGeometry geometry, int importance, Family family) {}

  private final Queue<Container> containers = new ConcurrentLinkedQueue<>();
  private volatile STRtree index;

  public static boolean hasFamily(String poiClass) {
    return FAMILIES.containsKey(poiClass);
  }

  public static boolean isContainer(String poiClass, String subclass) {
    return hasFamily(poiClass) && CONTAINER_SUBCLASSES.contains(subclass);
  }

  /** Records a container polygon in world coordinates. Safe to call from many threads. */
  public void add(long id, Geometry worldGeometry, int importance, String poiClass) {
    containers.add(new Container(id, PreparedGeometryFactory.prepare(worldGeometry), importance,
      FAMILIES.get(poiClass)));
  }

  /**
   * Returns {@code importance} halved if the point at world coordinates {@code (x, y)} lies inside a more important
   * container of its family other than the POI {@code id} itself, otherwise {@code importance} unchanged.
   */
  public int demote(long id, String poiClass, double x, double y, int importance) {
    Family family = FAMILIES.get(poiClass);
    if (family == null) {
      return importance;
    }
    var point = GeoUtils.JTS_FACTORY.createPoint(new Coordinate(x, y));
    @SuppressWarnings("unchecked") List<Container> candidates = index().query(new Envelope(x, x, y, y));
    for (Container container : candidates) {
      if (container.family == family && container.id != id && container.importance > importance &&
        container.geometry.covers(point)) {
        return importance / 2;
      }
    }
    return importance;
  }

  private STRtree index() {
    STRtree result = index;
    if (result == null) {
      synchronized (this) {
        result = index;
        if (result == null) {
          result = new STRtree();
          for (Container container : containers) {
            result.insert(container.geometry.getGeometry().getEnvelopeInternal(), container);
          }
          result.build();
          LOGGER.info("Indexed {} POI containers", containers.size());
          containers.clear();
          index = result;
        }
      }
    }
    return result;
  }
}
