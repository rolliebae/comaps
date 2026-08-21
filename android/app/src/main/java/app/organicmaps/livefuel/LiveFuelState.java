package app.organicmaps.livefuel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public final class LiveFuelState
{
  public final long stationId;
  public final double distanceKm;
  @NonNull public final String stationName;
  @NonNull public final List<FuelState> fuels;

  public LiveFuelState(long stationId, double distanceKm, @NonNull String stationName,
                       @NonNull List<FuelState> fuels)
  {
    this.stationId = stationId;
    this.distanceKm = distanceKm;
    this.stationName = stationName;
    this.fuels = Collections.unmodifiableList(fuels);
  }

  public static final class FuelState
  {
    @NonNull public final String code;
    @NonNull public final String name;
    @NonNull public final String status;
    public final double confidence;
    @Nullable public final Double price;
    @Nullable public final String lastReportAt;

    public FuelState(@NonNull String code, @NonNull String name, @NonNull String status,
                     double confidence, @Nullable Double price, @Nullable String lastReportAt)
    {
      this.code = code;
      this.name = name;
      this.status = status;
      this.confidence = confidence;
      this.price = price;
      this.lastReportAt = lastReportAt;
    }
  }
}
