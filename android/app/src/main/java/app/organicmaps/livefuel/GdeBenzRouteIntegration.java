package app.organicmaps.livefuel;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.R;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import java.util.Locale;

/**
 * Browser-only bridge for the Zernograd -> Moscow Rosneft AI-95 route.
 *
 * GdeBenz is intentionally opened as an external public website. We do not call,
 * scrape, mirror, cache, or reverse-engineer its station-status data. A stable
 * permanent public permalink for an individual station is not assumed here;
 * CoMaps keeps the exact station identity by coordinates and opens the closest
 * supported public GdeBenz route/local page.
 */
public final class GdeBenzRouteIntegration
{
  public static final String ROSNEFT_URL = "https://gdebenz.ru/brand/rosneft";
  public static final String AI95_URL = "https://gdebenz.ru/fuel/ai-95";
  public static final String M4_URL = "https://gdebenz.ru/trassa/m4-don";
  public static final String STUPINO_URL = "https://gdebenz.ru/gde-zapravitsya/stupino";
  public static final String RESERVE_ROSNEFT_URL = "https://www.gdebenz.org/brand/rosneft";

  private static final double MAX_ROUTE_STOP_DISTANCE_KM = 0.75;

  private static final RouteStop[] ROUTE_STOPS = {
      new RouteStop("Роснефть М-4 711 км", 50.174728, 40.407089, M4_URL),
      new RouteStop("Роснефть М-4 424 км", 52.376106, 38.894249, M4_URL),
      new RouteStop("Роснефть М-4 108 км", 54.850270, 38.039720, STUPINO_URL)
  };

  private GdeBenzRouteIntegration() {}

  public static boolean isSupportedRouteStop(@NonNull MapObject mapObject)
  {
    if (findPlannedStop(mapObject.getLat(), mapObject.getLon()) != null)
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

  /**
   * Kept as the button entry point used by LiveFuelCardView. The selected map
   * object is read from the existing place-page ViewModel so an imported GPX
   * waypoint can be matched to one of the three planned stations.
   */
  public static void openRosneft(@NonNull Context context)
  {
    MapObject mapObject = currentMapObject(context);
    RouteStop stop = mapObject == null ? null : findPlannedStop(mapObject.getLat(), mapObject.getLon());

    if (stop != null)
    {
      if (tryOpenWeb(context, stop.primaryUrl))
        return;
      if (tryOpenWeb(context, RESERVE_ROSNEFT_URL))
        return;
      if (tryOpenGeo(context, stop))
        return;
    }
    else
    {
      if (tryOpenWeb(context, ROSNEFT_URL))
        return;
      if (tryOpenWeb(context, AI95_URL))
        return;
      if (tryOpenWeb(context, RESERVE_ROSNEFT_URL))
        return;
    }

    Toast.makeText(context, R.string.livefuel_gdebenz_open_failed, Toast.LENGTH_LONG).show();
  }

  @Nullable
  private static MapObject currentMapObject(@NonNull Context context)
  {
    FragmentActivity activity = findActivity(context);
    if (activity == null)
      return null;
    return new ViewModelProvider(activity).get(PlacePageViewModel.class).getMapObject().getValue();
  }

  @Nullable
  private static FragmentActivity findActivity(@NonNull Context context)
  {
    Context current = context;
    while (current instanceof ContextWrapper)
    {
      if (current instanceof FragmentActivity activity)
        return activity;
      current = ((ContextWrapper) current).getBaseContext();
    }
    return current instanceof FragmentActivity ? (FragmentActivity) current : null;
  }

  private static boolean tryOpenWeb(@NonNull Context context, @NonNull String url)
  {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    if (!(context instanceof Activity))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    if (intent.resolveActivity(context.getPackageManager()) == null)
      return false;

    try
    {
      context.startActivity(intent);
      return true;
    }
    catch (ActivityNotFoundException | SecurityException ignored)
    {
      return false;
    }
  }

  private static boolean tryOpenGeo(@NonNull Context context, @NonNull RouteStop stop)
  {
    String query = stop.lat + "," + stop.lon + "(" + Uri.encode(stop.name + " · АИ-95") + ")";
    Uri uri = Uri.parse("geo:" + stop.lat + "," + stop.lon + "?q=" + query);
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    if (!(context instanceof Activity))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    if (intent.resolveActivity(context.getPackageManager()) == null)
      return false;

    try
    {
      context.startActivity(intent);
      return true;
    }
    catch (ActivityNotFoundException | SecurityException ignored)
    {
      return false;
    }
  }

  @Nullable
  private static RouteStop findPlannedStop(double lat, double lon)
  {
    for (RouteStop stop : ROUTE_STOPS)
    {
      if (distanceKm(lat, lon, stop.lat, stop.lon) <= MAX_ROUTE_STOP_DISTANCE_KM)
        return stop;
    }
    return null;
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

  private static final class RouteStop
  {
    @NonNull final String name;
    final double lat;
    final double lon;
    @NonNull final String primaryUrl;

    RouteStop(@NonNull String name, double lat, double lon, @NonNull String primaryUrl)
    {
      this.name = name;
      this.lat = lat;
      this.lon = lon;
      this.primaryUrl = primaryUrl;
    }
  }
}
