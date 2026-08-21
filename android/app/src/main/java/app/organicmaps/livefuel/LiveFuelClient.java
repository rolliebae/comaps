package app.organicmaps.livefuel;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.downloader.Android7RootCertificateWorkaround;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LiveFuelClient
{
  private static final int TIMEOUT_MS = 8000;
  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private LiveFuelClient() {}

  public interface Callback<T>
  {
    void onSuccess(@NonNull T value);
    void onError(@NonNull Exception error);
  }

  public static void loadNearestStation(@NonNull Context context, double lat, double lon,
                                        @NonNull Callback<LiveFuelState> callback)
  {
    final String baseUrl = baseUrl(context);
    EXECUTOR.execute(() -> {
      try
      {
        Uri uri = Uri.parse(baseUrl + "/api/v1/stations/nearby").buildUpon()
            .appendQueryParameter("lat", Double.toString(lat))
            .appendQueryParameter("lon", Double.toString(lon))
            .appendQueryParameter("radius_km", "0.5")
            .appendQueryParameter("limit", "1")
            .build();

        JSONArray nearby = new JSONArray(get(uri.toString()));
        if (nearby.length() == 0)
          throw new IOException("No LiveFuel station found near selected POI");

        JSONObject nearest = nearby.getJSONObject(0);
        double distanceKm = nearest.optDouble("distance_km", 999.0);
        if (distanceKm > 0.35)
          throw new IOException("Nearest LiveFuel station is more than 350 m away");

        long stationId = nearest.getLong("id");
        JSONObject detail = new JSONObject(get(baseUrl + "/api/v1/stations/" + stationId));
        String stationName = detail.optString("name", "");
        JSONArray fuelArray = detail.optJSONArray("fuels");
        List<LiveFuelState.FuelState> fuels = new ArrayList<>();

        if (fuelArray != null)
        {
          for (int i = 0; i < fuelArray.length(); i++)
          {
            JSONObject fuel = fuelArray.getJSONObject(i);
            String code = fuel.optString("code", "");
            String name = fuel.optString("name", code);
            String status = fuel.isNull("status") ? "unknown" : fuel.optString("status", "unknown");
            double confidence = fuel.isNull("confidence") ? 0.0 : fuel.optDouble("confidence", 0.0);
            Double price = fuel.isNull("price_median") ? null : fuel.optDouble("price_median");
            String lastReportAt = fuel.isNull("last_report_at") ? null : fuel.optString("last_report_at", null);
            fuels.add(new LiveFuelState.FuelState(code, name, status, confidence, price, lastReportAt));
          }
        }

        LiveFuelState state = new LiveFuelState(stationId, distanceKm, stationName, fuels);
        MAIN.post(() -> callback.onSuccess(state));
      }
      catch (Exception e)
      {
        MAIN.post(() -> callback.onError(e));
      }
    });
  }

  public static void submitReport(@NonNull Context context, long stationId, @NonNull String deviceId,
                                  @NonNull String generalStatus,
                                  @NonNull List<LiveFuelState.FuelState> fuels,
                                  @Nullable Location location, @NonNull Callback<Long> callback)
  {
    final String baseUrl = baseUrl(context);
    EXECUTOR.execute(() -> {
      try
      {
        JSONObject body = new JSONObject();
        body.put("station_id", stationId);
        body.put("observed_at", Instant.now().toString());
        body.put("general_status", generalStatus);
        body.put("platform", "android");

        JSONArray fuelArray = new JSONArray();
        for (LiveFuelState.FuelState fuel : fuels)
        {
          if ("unknown".equals(fuel.status))
            continue;

          JSONObject item = new JSONObject();
          item.put("type", fuel.code);
          item.put("availability", fuel.status);
          if (fuel.price != null)
            item.put("price", fuel.price);
          fuelArray.put(item);
        }
        body.put("fuels", fuelArray);

        if (location != null)
        {
          JSONObject proof = new JSONObject();
          proof.put("lat", location.getLatitude());
          proof.put("lon", location.getLongitude());
          if (location.hasAccuracy())
            proof.put("accuracy", location.getAccuracy());
          body.put("location_proof", proof);
        }

        JSONObject response = new JSONObject(post(baseUrl + "/api/v1/reports", body.toString(), deviceId));
        long reportId = response.getLong("id");
        MAIN.post(() -> callback.onSuccess(reportId));
      }
      catch (Exception e)
      {
        MAIN.post(() -> callback.onError(e));
      }
    });
  }

  @NonNull
  private static String baseUrl(@NonNull Context context)
  {
    String value = context.getString(R.string.livefuel_api_base_url).trim();
    while (value.endsWith("/"))
      value = value.substring(0, value.length() - 1);
    return value;
  }

  @NonNull
  private static String get(@NonNull String url) throws IOException
  {
    HttpURLConnection connection = open(url);
    connection.setRequestMethod("GET");
    return execute(connection, null);
  }

  @NonNull
  private static String post(@NonNull String url, @NonNull String body, @NonNull String deviceId) throws IOException
  {
    HttpURLConnection connection = open(url);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("X-Device-Id", deviceId);
    connection.setDoOutput(true);
    byte[] data = body.getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(data.length);
    return execute(connection, data);
  }

  private static HttpURLConnection open(@NonNull String url) throws IOException
  {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    Android7RootCertificateWorkaround.applyFixIfNeeded(connection);
    connection.setConnectTimeout(TIMEOUT_MS);
    connection.setReadTimeout(TIMEOUT_MS);
    connection.setUseCaches(false);
    return connection;
  }

  @NonNull
  private static String execute(@NonNull HttpURLConnection connection, @Nullable byte[] requestBody)
      throws IOException
  {
    try
    {
      if (requestBody != null)
      {
        try (OutputStream output = connection.getOutputStream())
        {
          output.write(requestBody);
        }
      }

      int code = connection.getResponseCode();
      InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
      String response = input == null ? "" : readAll(input);
      if (code < 200 || code >= 300)
        throw new IOException("LiveFuel HTTP " + code + ": " + response);
      return response;
    }
    finally
    {
      connection.disconnect();
    }
  }

  @NonNull
  private static String readAll(@NonNull InputStream input) throws IOException
  {
    try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream())
    {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1)
        out.write(buffer, 0, read);
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
