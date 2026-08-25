package app.organicmaps.livefuel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import java.util.Locale;

/**
 * Browser-only bridge for the Zernograd -> Moscow Rosneft AI-95 route.
 *
 * GdeBenz is intentionally opened as an external website. We do not call, scrape,
 * mirror, cache, or reverse-engineer its station-status data.
 */
public final class GdeBenzRouteIntegration
{
  public static final String ROSNEFT_URL = "https://gdebenz.ru/brand/rosneft";
  public static final String AI95_URL = "https://gdebenz.ru/fuel/ai-95";

  private static final double MAX_ROUTE_STOP_DISTANCE_KM = 0.75;
  private static final double[][] ROUTE_STOPS = {
      {50.174728, 40.407089}, // M-4, 711 km, Verkhniy Mamon
      {52.376106, 38.894249}, // M-4, 424 km, Zadonsk district
      {54.850270, 38.039720}  // M-4, 108 km, Stupino
  };

  private GdeBenzRouteIntegration() {}

  public static boolean isSupportedRouteStop(@NonNull MapObject mapObject)
  {
    if (isNearPlannedStop(mapObject.getLat(), mapObject.getLon()))
      return true;

    if (!LiveFuelMapObjectUtils.isFuelStation(mapObject))
      return false;

    StringBuilder text = new StringBuilder(mapObject.getTitle());
    if (mapObject.getSecondaryTitle() != null)
      text.append(' ').append(mapObject.getSecondaryTitle());
    text.append(' ').append(mapObject.getSubtitle());

    String normalized = text.toString().toLowerCase(Locale.ROOT);
    return normalized.contains("роснефть") || normalized.contains("rosneft");
  }

  public static void openRosneft(@NonNull Context context)
  {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ROSNEFT_URL));
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    if (!(context instanceof Activity))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  private static boolean isNearPlannedStop(double lat, double lon)
  {
    for (double[] stop : ROUTE_STOPS)
    {
      if (distanceKm(lat, lon, stop[0], stop[1]) <= MAX_ROUTE_STOP_DISTANCE_KM)
        return true;
    }
    return false;
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
}
