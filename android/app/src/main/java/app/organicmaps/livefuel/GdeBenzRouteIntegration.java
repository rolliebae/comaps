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
 * GdeBenz is intentionally opened as an external public website. We do not
 * call, scrape, mirror, cache, or reverse-engineer station-status data.
 * Exact station identity stays in CoMaps via FuelStationSourceBinding.
 */
public final class GdeBenzRouteIntegration
{
  public static final String ROSNEFT_URL = "https://gdebenz.ru/brand/rosneft";
  public static final String AI95_URL = "https://gdebenz.ru/fuel/ai-95";
  public static final String RESERVE_ROSNEFT_URL = "https://www.gdebenz.org/brand/rosneft";

  private GdeBenzRouteIntegration() {}

  public static boolean isSupportedRouteStop(@NonNull MapObject mapObject)
  {
    if (FuelStationSourceBinding.isKnownRouteStation(mapObject))
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

  @Nullable
  static FuelStationSourceBinding.Binding bindingFor(@Nullable MapObject mapObject)
  {
    return mapObject == null ? null : FuelStationSourceBinding.find(mapObject);
  }

  public static void openRosneft(@NonNull Context context)
  {
    FuelStationSourceBinding.Binding binding = bindingFor(currentMapObject(context));

    if (binding != null)
    {
      if (tryOpenWeb(context, binding.gdeBenzPrimaryUrl))
        return;
      if (tryOpenWeb(context, AI95_URL))
        return;
      if (tryOpenWeb(context, RESERVE_ROSNEFT_URL))
        return;
      if (tryOpenWeb(context, FuelStationSourceBinding.ROSNEFT_STATIONS_URL))
        return;
      if (tryOpenGeo(context, binding))
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
      if (tryOpenWeb(context, FuelStationSourceBinding.ROSNEFT_STATIONS_URL))
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

  private static boolean tryOpenGeo(@NonNull Context context,
                                    @NonNull FuelStationSourceBinding.Binding binding)
  {
    String label = binding.rosneftStationId + " · АИ-95";
    String query = binding.lat + "," + binding.lon + "(" + Uri.encode(label) + ")";
    Uri uri = Uri.parse("geo:" + binding.lat + "," + binding.lon + "?q=" + query);
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
}
