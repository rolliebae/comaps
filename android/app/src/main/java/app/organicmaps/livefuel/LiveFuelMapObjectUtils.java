package app.organicmaps.livefuel;

import android.util.Log;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import java.lang.reflect.Field;
import java.util.List;

public final class LiveFuelMapObjectUtils
{
  private static final String TAG = LiveFuelMapObjectUtils.class.getSimpleName();
  private static final Field RAW_TYPES_FIELD = findRawTypesField();

  private LiveFuelMapObjectUtils() {}

  private static Field findRawTypesField()
  {
    try
    {
      Field field = MapObject.class.getDeclaredField("mRawTypes");
      field.setAccessible(true);
      return field;
    }
    catch (ReflectiveOperationException e)
    {
      Log.e(TAG, "Unable to access MapObject raw types", e);
      return null;
    }
  }

  public static boolean isFuelStation(@NonNull MapObject mapObject)
  {
    if (RAW_TYPES_FIELD == null)
      return false;

    try
    {
      Object value = RAW_TYPES_FIELD.get(mapObject);
      if (!(value instanceof List<?> types))
        return false;

      for (Object type : types)
      {
        if ("amenity-fuel".equals(type))
          return true;
      }
    }
    catch (IllegalAccessException e)
    {
      Log.e(TAG, "Unable to read MapObject raw types", e);
    }
    return false;
  }
}
