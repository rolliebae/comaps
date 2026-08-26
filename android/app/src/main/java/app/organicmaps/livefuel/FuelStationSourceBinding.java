package app.organicmaps.livefuel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.bookmarks.data.MapObject;

/**
 * Static source binding for the three planned Rosneft AI-95 stops.
 *
 * CoMaps/OSM remains the authority for geometry and routing. Rosneft public
 * sources provide station identity where a current official identifier can be
 * verified. GdeBenz remains a browser-only, crowdsourced live-status layer.
 *
 * OSM object ids are intentionally not frozen here. OSM nodes/ways may be
 * remapped; the route is bound to coordinates plus the OSM fuel-station
 * semantics used by CoMaps.
 */
public final class FuelStationSourceBinding
{
  public static final String ROSNEFT_STATIONS_URL = "https://rosneft-azs.ru/stations";
  public static final String OSM_AMENITY = "fuel";
  public static final String OSM_BRAND = "Роснефть";
  public static final String OSM_AI95_TAG = "fuel:octane_95";
  public static final String OSM_AI95_VALUE = "yes";

  private static final double MATCH_RADIUS_KM = 0.75;

  private static final Binding[] ROUTE_STATIONS = {
      new Binding(
          "rosneft-50",
          "АЗС №50",
          50.174728,
          40.407089,
          "Воронежская область, Верхний Мамон, ул. Дорожная, 30 / М-4, 711 км",
          false,
          "https://lk.ariscard.com/download/rus_2.pdf",
          "https://gdebenz.ru/trassa/m4-don"),
      new Binding(
          "rosneft-281",
          "АЗК №281",
          52.376106,
          38.894249,
          "Липецкая область, Большое Панарино, М-4, 424 км, зд. 94а",
          true,
          "https://rosneft-azs.ru/upload/site1/document_news/2025/Spisok_obnovlennykh_AZK2.pdf",
          "https://gdebenz.ru/trassa/m4-don"),
      new Binding(
          "rosneft-mj247",
          "MJ247",
          54.850270,
          38.039720,
          "Московская область, Ступинский район, а/д Москва-Ростов-Дон, 108 км, левая сторона",
          true,
          "https://rosneft-azs.ru/news/169599",
          "https://gdebenz.ru/gde-zapravitsya/stupino")
  };

  private FuelStationSourceBinding() {}

  @Nullable
  static Binding find(@NonNull MapObject mapObject)
  {
    return find(mapObject.getLat(), mapObject.getLon());
  }

  @Nullable
  static Binding find(double lat, double lon)
  {
    for (Binding binding : ROUTE_STATIONS)
    {
      if (distanceKm(lat, lon, binding.lat, binding.lon) <= MATCH_RADIUS_KM)
        return binding;
    }
    return null;
  }

  static boolean isKnownRouteStation(@NonNull MapObject mapObject)
  {
    return find(mapObject) != null;
  }

  private static double distanceKm(double lat1, double lon1, double lat2, double lon2)
  {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 6371.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
  }

  static final class Binding
  {
    @NonNull final String routeId;
    @NonNull final String rosneftStationId;
    final double lat;
    final double lon;
    @NonNull final String address;
    final boolean officialIdentityVerified;
    @NonNull final String identitySourceUrl;
    @NonNull final String gdeBenzPrimaryUrl;

    Binding(@NonNull String routeId,
            @NonNull String rosneftStationId,
            double lat,
            double lon,
            @NonNull String address,
            boolean officialIdentityVerified,
            @NonNull String identitySourceUrl,
            @NonNull String gdeBenzPrimaryUrl)
    {
      this.routeId = routeId;
      this.rosneftStationId = rosneftStationId;
      this.lat = lat;
      this.lon = lon;
      this.address = address;
      this.officialIdentityVerified = officialIdentityVerified;
      this.identitySourceUrl = identitySourceUrl;
      this.gdeBenzPrimaryUrl = gdeBenzPrimaryUrl;
    }
  }
}
