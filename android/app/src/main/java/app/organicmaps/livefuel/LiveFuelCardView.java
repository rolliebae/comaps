package app.organicmaps.livefuel;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LiveFuelCardView extends LinearLayout
{
  private static final String PREFS_NAME = "livefuel";
  private static final String PREF_DEVICE_ID = "device_id";

  private CircularProgressIndicator mProgress;
  private MaterialTextView mStatus;
  private MaterialTextView mMeta;
  private MaterialButton mConfirm;
  private MaterialButton mReport;
  private MaterialButton mGdeBenz;

  @Nullable private PlacePageViewModel mViewModel;
  @Nullable private Observer<MapObject> mObserver;
  @Nullable private MapObject mMapObject;
  @Nullable private LiveFuelState mState;

  public LiveFuelCardView(@NonNull Context context)
  {
    this(context, null);
  }

  public LiveFuelCardView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public LiveFuelCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    setOrientation(VERTICAL);
    inflate(context, R.layout.livefuel_card_view, this);
    setVisibility(GONE);

    mProgress = findViewById(R.id.livefuel_progress);
    mStatus = findViewById(R.id.livefuel_status);
    mMeta = findViewById(R.id.livefuel_meta);
    mConfirm = findViewById(R.id.livefuel_confirm);
    mReport = findViewById(R.id.livefuel_report);
    mGdeBenz = findViewById(R.id.livefuel_gdebenz);

    mConfirm.setOnClickListener(v -> confirmCurrentState());
    mReport.setOnClickListener(v -> chooseFuelForReport());
    mGdeBenz.setOnClickListener(v -> GdeBenzRouteIntegration.openRosneft(getContext()));
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    FragmentActivity activity = findActivity(getContext());
    if (activity == null)
      return;

    mViewModel = new ViewModelProvider(activity).get(PlacePageViewModel.class);
    mObserver = this::onMapObjectChanged;
    mViewModel.getMapObject().observe(activity, mObserver);
  }

  @Override
  protected void onDetachedFromWindow()
  {
    if (mViewModel != null && mObserver != null)
      mViewModel.getMapObject().removeObserver(mObserver);
    mObserver = null;
    mViewModel = null;
    super.onDetachedFromWindow();
  }

  private void onMapObjectChanged(@Nullable MapObject mapObject)
  {
    mMapObject = mapObject;
    mState = null;

    boolean isFuelStation = mapObject != null && LiveFuelMapObjectUtils.isFuelStation(mapObject);
    boolean isGdeBenzStop = mapObject != null && GdeBenzRouteIntegration.isSupportedRouteStop(mapObject);
    if (mapObject == null || (!isFuelStation && !isGdeBenzStop))
    {
      setVisibility(GONE);
      return;
    }

    setVisibility(VISIBLE);
    mGdeBenz.setVisibility(isGdeBenzStop ? VISIBLE : GONE);
    mGdeBenz.setEnabled(true);

    // Imported route waypoints are bookmarks, not OSM fuel POIs. Keep the
    // GdeBenz route check available even when LiveFuel has no POI to query.
    if (!isFuelStation)
    {
      setLoading(false);
      mStatus.setText(R.string.livefuel_gdebenz_route_stop);
      mMeta.setText(R.string.livefuel_gdebenz_disclaimer);
      mConfirm.setEnabled(false);
      mReport.setEnabled(false);
      return;
    }

    setLoading(true);
    mStatus.setText(R.string.livefuel_loading);
    mMeta.setText("");

    LiveFuelClient.loadNearestStation(getContext(), mapObject.getLat(), mapObject.getLon(),
                                      new LiveFuelClient.Callback<>() {
      @Override
      public void onSuccess(@NonNull LiveFuelState value)
      {
        if (!isAttachedToWindow() || mMapObject != mapObject)
          return;
        mState = value;
        render(value);
      }

      @Override
      public void onError(@NonNull Exception error)
      {
        if (!isAttachedToWindow() || mMapObject != mapObject)
          return;
        setLoading(false);
        mStatus.setText(R.string.livefuel_no_live_data);
        if (isGdeBenzStop)
          mMeta.setText(R.string.livefuel_gdebenz_disclaimer);
        else
          mMeta.setText(error.getMessage());
        mConfirm.setEnabled(false);
        mReport.setEnabled(false);
      }
    });
  }

  private void render(@NonNull LiveFuelState state)
  {
    setLoading(false);
    StringBuilder lines = new StringBuilder();
    Instant newest = null;
    int usable = 0;

    for (LiveFuelState.FuelState fuel : state.fuels)
    {
      if ("unknown".equals(fuel.status))
        continue;

      usable++;
      if (lines.length() > 0)
        lines.append('\n');
      lines.append(fuel.name).append("  •  ").append(statusLabel(fuel.status));
      if (fuel.confidence > 0)
        lines.append("  ").append(Math.round(fuel.confidence * 100)).append('%');
      if (fuel.price != null)
        lines.append(String.format(Locale.getDefault(), "  %.2f", fuel.price));

      if (fuel.lastReportAt != null)
      {
        try
        {
          Instant time = Instant.parse(fuel.lastReportAt);
          if (newest == null || time.isAfter(newest))
            newest = time;
        }
        catch (Exception ignored) {}
      }
    }

    if (usable == 0)
      lines.append(getContext().getString(R.string.livefuel_no_reports));

    mStatus.setText(lines.toString());
    if (newest != null)
    {
      long minutes = Math.max(0, Duration.between(newest, Instant.now()).toMinutes());
      mMeta.setText(getContext().getString(R.string.livefuel_updated_minutes, minutes));
    }
    else
      mMeta.setText(R.string.livefuel_waiting_for_reports);

    mConfirm.setEnabled(usable > 0);
    mReport.setEnabled(true);
  }

  private void confirmCurrentState()
  {
    LiveFuelState state = mState;
    if (state == null)
      return;

    List<LiveFuelState.FuelState> fuels = new ArrayList<>();
    boolean anyAvailable = false;
    for (LiveFuelState.FuelState fuel : state.fuels)
    {
      if ("unknown".equals(fuel.status))
        continue;
      fuels.add(fuel);
      if (!"unavailable".equals(fuel.status))
        anyAvailable = true;
    }

    if (!fuels.isEmpty())
      submit(anyAvailable ? "available" : "unavailable", fuels);
  }

  private void chooseFuelForReport()
  {
    LiveFuelState state = mState;
    if (state == null)
      return;

    List<LiveFuelState.FuelState> candidates = new ArrayList<>(state.fuels);
    if (candidates.isEmpty())
    {
      candidates.add(new LiveFuelState.FuelState("ai92", "АИ-92", "unknown", 0, null, null));
      candidates.add(new LiveFuelState.FuelState("ai95", "АИ-95", "unknown", 0, null, null));
      candidates.add(new LiveFuelState.FuelState("diesel", "ДТ", "unknown", 0, null, null));
    }

    String[] labels = new String[candidates.size()];
    for (int i = 0; i < candidates.size(); i++)
      labels[i] = candidates.get(i).name;

    new AlertDialog.Builder(getContext())
        .setTitle(R.string.livefuel_choose_fuel)
        .setItems(labels, (dialog, which) -> chooseStatusForReport(candidates.get(which)))
        .show();
  }

  private void chooseStatusForReport(@NonNull LiveFuelState.FuelState fuel)
  {
    String[] labels = {
        getContext().getString(R.string.livefuel_status_available),
        getContext().getString(R.string.livefuel_status_queue),
        getContext().getString(R.string.livefuel_status_low),
        getContext().getString(R.string.livefuel_status_unavailable),
        getContext().getString(R.string.livefuel_status_limited)
    };
    String[] general = {"available", "queue", "low", "unavailable", "limited"};
    String[] availability = {"available", "available", "low", "unavailable", "limited"};

    new AlertDialog.Builder(getContext())
        .setTitle(fuel.name)
        .setItems(labels, (dialog, which) -> {
          LiveFuelState.FuelState reportFuel =
              new LiveFuelState.FuelState(fuel.code, fuel.name, availability[which], 0, null, null);
          submit(general[which], List.of(reportFuel));
        })
        .show();
  }

  private void submit(@NonNull String generalStatus, @NonNull List<LiveFuelState.FuelState> fuels)
  {
    LiveFuelState state = mState;
    if (state == null)
      return;

    setLoading(true);
    Location location = MwmApplication.from(getContext()).getLocationHelper().getSavedLocation();
    LiveFuelClient.submitReport(getContext(), state.stationId, deviceId(), generalStatus, fuels, location,
                                new LiveFuelClient.Callback<>() {
      @Override
      public void onSuccess(@NonNull Long reportId)
      {
        if (!isAttachedToWindow())
          return;
        MapObject current = mMapObject;
        if (current != null)
          onMapObjectChanged(current);
      }

      @Override
      public void onError(@NonNull Exception error)
      {
        if (!isAttachedToWindow())
          return;
        setLoading(false);
        mMeta.setText(getContext().getString(R.string.livefuel_submit_failed, error.getMessage()));
        mConfirm.setEnabled(true);
        mReport.setEnabled(true);
      }
    });
  }

  private void setLoading(boolean loading)
  {
    mProgress.setVisibility(loading ? VISIBLE : GONE);
    mConfirm.setEnabled(!loading);
    mReport.setEnabled(!loading);
    // External GdeBenz lookup does not depend on the LiveFuel backend.
    if (mGdeBenz.getVisibility() == VISIBLE)
      mGdeBenz.setEnabled(true);
  }

  @NonNull
  private String deviceId()
  {
    SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String id = prefs.getString(PREF_DEVICE_ID, null);
    if (id != null)
      return id;

    id = UUID.randomUUID().toString();
    prefs.edit().putString(PREF_DEVICE_ID, id).apply();
    return id;
  }

  @NonNull
  private String statusLabel(@NonNull String status)
  {
    return switch (status)
    {
      case "available" -> getContext().getString(R.string.livefuel_status_available);
      case "queue" -> getContext().getString(R.string.livefuel_status_queue);
      case "low" -> getContext().getString(R.string.livefuel_status_low);
      case "unavailable" -> getContext().getString(R.string.livefuel_status_unavailable);
      case "limited" -> getContext().getString(R.string.livefuel_status_limited);
      default -> getContext().getString(R.string.livefuel_status_unknown);
    };
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
}
