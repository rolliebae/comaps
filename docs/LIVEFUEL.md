# LiveFuel MVP

LiveFuel is a crowdsourced dynamic fuel-status layer for CoMaps fuel stations.

## Scope of the first Android MVP

- CoMaps continues to provide offline maps, POIs, search and routing.
- LiveFuel status is fetched from a separate HTTP API and never written into `.mwm` map files.
- The Android place page shows live fuel availability only for `amenity=fuel` objects.
- Users can confirm current state or report a change for a selected fuel type.
- A random installation identifier is sent in `X-Device-Id` for anti-abuse weighting.
- The app can send the saved device location as transient proof; the reference backend converts it to distance-to-station.

## Local development

The Android resource `livefuel_api_base_url` defaults to:

```text
http://10.0.2.2:8000
```

This points an Android Emulator at a FastAPI service running on the development host.

## Backend contract

The client currently uses:

```text
GET  /api/v1/stations/nearby
GET  /api/v1/stations/{station_id}
POST /api/v1/reports
```

The next client milestone is Smart Pick: rank credible nearby stations server-side, then re-rank the best candidates with actual CoMaps route distance/ETA.

## CI validation

The fork contains a GitHub Actions workflow that performs a shallow parallel recursive submodule checkout and builds an arm64 `FdroidDebug` APK on pushes to `main` and on pull requests.
