# LiveFuel MVP

LiveFuel is a crowdsourced dynamic fuel-status layer for CoMaps fuel stations.

## Scope of the first Android MVP

- CoMaps continues to provide offline maps, POIs, search and routing.
- LiveFuel data is fetched from a separate HTTP API and is never written into `.mwm` map files.
- The Android place page shows LiveFuel status only for objects identified as fuel stations (`amenity=fuel`).
- Users can confirm the current state or report a change for a selected fuel type.
- A random installation identifier is sent in `X-Device-Id` for anti-abuse weighting. It identifies an app installation, not a user account.
- Location used as report evidence is transient input to the backend and must not become a requirement for normal CoMaps map, search or routing behavior.
- If the LiveFuel service is unavailable, CoMaps must fall back to the normal fuel-station place page rather than blocking the underlying offline experience.

Smart Pick is intentionally **out of scope for this MVP**. It is a follow-up milestone that may rank credible nearby stations server-side and then re-rank candidates using actual CoMaps route distance/ETA.

## Local development

The Android resource `livefuel_api_base_url` defaults to:

```text
http://10.0.2.2:8000
```

This points an Android Emulator at a FastAPI service running on the development host.

## Backend API surface

The client currently targets:

```text
GET  /api/v1/stations/nearby
GET  /api/v1/stations/{station_id}
POST /api/v1/reports
```

This document records the MVP API surface, not yet a complete wire-level contract. Before client/backend compatibility is treated as frozen, the implementation must define:

- request query/body fields and their units;
- response JSON fields and nullability;
- HTTP error behavior;
- fuel price representation and currency;
- freshness/timestamp format and semantics;
- nearby-search radius semantics and limits.

### Station identity

`amenity=fuel` determines whether a CoMaps object is eligible for LiveFuel; it is **not** a unique station identifier.

`station_id` must map to a stable identifier for one physical station. The exact mapping (for example, an OSM element identity or another stable CoMaps-backed key) is not frozen by this document and must be decided before station records are persisted or shared between clients and the backend.

### Device and location semantics

`X-Device-Id` is a random installation-scoped identifier used only for anti-abuse/reputation weighting. It must not be treated as an authenticated user identity.

When location is supplied as evidence for a report, the client/backend contract must state which location source is used (for example, the device location available at report time) and how long that evidence is retained. LiveFuel must continue to degrade gracefully when location permission or the LiveFuel backend is unavailable.

## CI validation

The fork contains a GitHub Actions workflow intended to perform a shallow parallel recursive submodule checkout and build an arm64 `FdroidDebug` APK on pushes to `main` and on pull requests.

The presence of the workflow does not by itself prove that a PR is validated. A PR should only claim Android CI validation after the corresponding workflow completes successfully, including the `FdroidDebug` build step.
