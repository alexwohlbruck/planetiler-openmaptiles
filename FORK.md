# Parchment fork

This fork renders Parchment's basemap. It tracks
[openmaptiles/planetiler-openmaptiles](https://github.com/openmaptiles/planetiler-openmaptiles)
and changes only what is listed here. Merge upstream releases rather than rebasing, because
published tags point into this history.

## Releases

Tag `v<upstream>-importance.N` (for example `v3.16-importance.1`). The `Parchment image` workflow
publishes `ghcr.io/alexwohlbruck/planetiler-openmaptiles:<tag>` and `:latest`, which barrelman's
`scripts/rebuild-basemap.sh` runs. Bump `N` for fork changes and restart it at 1 after an upstream
merge.

## POI importance

Upstream sorts each POI label-grid cell by a fixed category table (`Poi.CLASS_RANKS`) and breaks
ties on input order. `rank` is then the POI's position in its cell. Two hospitals tie, so a
flagship hospital can lose its label to a small neighbour, and a city's main park can rank below
pocket parks.

The fork sorts by an **importance** score and writes it to the tiles.

### Tile surface

- `poi` layer: new integer attribute `importance`, 0–100, on named POIs only. Higher is more
  prominent. It is not part of the OpenMapTiles schema.
- `rank` keeps its meaning (1-based position in the 64px label grid at z14), but follows the new
  order.
- Named POIs with `importance >= 50` appear from z12, and with `importance >= 42` from z13
  (`Poi.Z12_IMPORTANCE`, `Poi.Z13_IMPORTANCE`). Everything else keeps upstream's minzoom: 14, or 12
  for railway stations, halts and ferry terminals, or 10 for large universities. A lower upstream
  minzoom is never raised.
- These thresholds are part of the tile contract. Parchment's style gates POIs on the same numbers
  (z12: `importance >= 50`, z13: `>= 42`), so change the tiles and every client's style together.

### Score

`PoiImportance.score` = category + fame + footprint.

|   Part    |   Range    |                                                                                                                                        Formula                                                                                                                                        |
|-----------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| category  | 0–30       | `round(30 × (1 − clamp(ln(classRank / 20) / ln(1000 / 20), 0, 1)))`, so hospital 30, park 16, shop 7, bar 2, unranked 0. Landmark classes upstream leaves unranked get ranks for this score only: museum and castle 70, monument 80, theatre and art gallery 90, place of worship 110 |
| fame      | 0 or 10–40 | 10 if tagged `wikidata` or `wikipedia`, plus `min(30, round(6 × log10(views / 1000)))` for QRank yearly pageviews above 1000                                                                                                                                                          |
| footprint | 0–25       | `min(25, round(6 × log10(m² / 1000)))` for polygons above 1000 m²; points score 0                                                                                                                                                                                                     |

Area is the polygon's Web Mercator area converted to m² and corrected by `cos²(latitude)` at its
centroid.

### Sort key

`SortKey.orderByTruesFirst(named).thenByInt(100 − importance, 0, 100).thenByInt(classRank, 0, 1000)`.
Unnamed POIs still sort after every named one, as upstream's `+2000` did.

### Pageviews

`--poi-qrank=<path>` reads a [QRank](https://qrank.wmcloud.org) CSV (`Entity,QRank`, `.gz`
allowed). Only items with at least 1000 views are kept in memory. Without the flag, fame counts
only the Wikidata or Wikipedia link.

### Containment

A POI inside a more important polygon POI of the same family has its `importance` halved (rounded
down), so a pharmacy does not outrank the hospital it sits in.

|  Family   | Containers (polygon subclass) |                                                                   Demoted classes                                                                    |
|-----------|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| health    | `hospital`                    | `hospital`, `pharmacy`, `doctors`, `dentist`                                                                                                         |
| retail    | `mall` (class `shop`)         | `shop`, `grocery`, `clothing_store`, `alcohol_shop`, `music`, `bakery`, `hairdresser`, `fast_food`, `restaurant`, `cafe`, `bar`, `beer`, `ice_cream` |
| education | `university`, `college`       | `college`, `school`, `library`                                                                                                                       |

The table lives in `PoiContainment.FAMILIES`. A container must be named and is matched by its
class's family, so `office=university` (class `office`) is not one.

- Pass 2 sees nodes before ways and relations, so containment is decided when tiles are written,
  not in `process()`. `Poi.process` stores each container's world polygon, importance, family and
  vector tile feature id; the first tile builds an STRtree over them.
- `Poi.postProcessTile` decodes each POI point to world coordinates and halves its importance once
  if any container of its family covers it, is not the POI itself (same feature id), and is
  strictly more important. A container's own centroid point is never demoted by its polygon.
- It then sorts the layer stably by importance (unnamed POIs last) and assigns `rank` from that
  order within each label-grid cell, so `rank` reflects demotion. Upstream assigns `rank` in a
  `LayerPostProcessor`; the fork moves it to this `TilePostProcessor`, the only hook given the
  tile coordinate.
- A POI scoring 0 or 1 stays 0, so most unlinked restaurants, cafés and pharmacies (category 0) are
  unaffected; the rule mostly moves campus buildings, hospital departments and linked shops.
- Only container polygons are held in memory, until the archive is written.
- `minzoom` is fixed before containment is known, so a demoted POI can still be in a z12 or z13
  tile below that zoom's threshold. Clients must apply the `importance` gate rather than trust
  presence.

### Porting notes

The native apps read the same tiles, so they get `importance`, the low-zoom POIs and the demotion
with no engine change. A style that gates POIs by `importance` instead of `rank` needs the same
thresholds on every platform: z12 `>= 50`, z13 `>= 42`.

## Building names

Upstream's `building` layer carries no names, and only buildings with a POI tag (`amenity`, `shop`,
…) reach the `poi` layer. A campus whose halls are tagged only `building=* name=*` shows no labels.

### Tile surface

- `poi` layer: a named OSM building polygon that the POI table does not already match is written
  as a point with `class=building` and `subclass` set to its `building` value (`school`, `yes`, …),
  plus the usual `name*` attributes. `building:part` alone does not qualify.
- `minzoom` 14 (`Poi.BUILDING_MINZOOM`); no `importance`, so it sorts and ranks after every named POI
  and never pushes a place down its label-grid cell. Among buildings, the larger footprint ranks
  first (`PoiImportance.footprint`).
- A building that is also a POI (`building=school amenity=theatre`) is emitted once, as that POI.

### Porting notes

No engine change. A style needs a text-only layer for `class == building` that sits below the POI
badges, so a place wins a collision with a building's name. Parchment shows it from z16.

## Lake label room

A lake's label point sits somewhere inside the lake, but nothing in the tiles says how much water
surrounds it. A long name on a narrow or island-filled lake spills over the shore. The fork writes
the room around the label so the client can show a name only when it fits inside the polygon.

### Tile surface

- `water_name` layer: new integer attributes `room_x` and `room_y` on the point label of an OSM
  water polygon (`WaterName.ROOM_X`, `WaterName.ROOM_Y`). They are not part of the OpenMapTiles
  schema. Lake centerlines, Natural Earth and marine points do not carry them.
- `room_x` is the length of the horizontal chord through the label point, clipped to the water
  polygon. Holes (islands) cut the chord, and only the segment containing the label point counts.
  `room_y` is the same for the vertical chord.
- Units are screen pixels at **z14 with 512px tiles**: `round(worldLength × 2^23)`, where world
  coordinates span 0..1. At zoom `z` the room is `room × 2^(z − 14)` pixels.
- Values are capped at 1,000,000.
- If the geometry cannot be read or the label point is not enclosed, both attributes are omitted.
  Clients treat a missing value as "fits".

### Computation

`WaterName.putLabelRoom` takes `SourceFeature.pointOnSurface()` (JTS `getInteriorPoint`, the
same cached point `features.pointOnSurface` emits) and the world geometry, repaired with
`GeometryFixer` when invalid. `WaterName.chord` walks every ring of every polygon, intersects each
edge with the axis line through the point, and keeps the nearest crossing on each side. Edges use
the half-open rule `(a0 > y) != (a1 > y)`, so a vertex on the line is counted once.

### Porting notes

The native apps read the same tiles. A style that hides lake names that overflow needs the same
test on every platform: show the label when its rendered width is at most
`room_x × 2^(zoom − 14)` and its height at most `room_y × 2^(zoom − 14)`, or when either attribute
is missing.
