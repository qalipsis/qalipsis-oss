# MockServer for QALIPSIS head

Mocks all read-only endpoints of the `head` module so the webapp at `http://localhost:3000` can run against a stable
demo dataset.

## Run

```bash
docker compose up -d
```

A one-shot `mockserver-init` container regenerates `initializer.json` from `initializer.template.json` before MockServer
starts, so all demo timestamps are rebased to "today" on every startup.

MockServer listens on `http://localhost:8401`. CORS is open to `http://localhost:3000`.

## Refresh dates without restarting

`MOCKSERVER_WATCH_INITIALIZATION_JSON=true` is set, so re-running the generator hot-reloads MockServer:

```bash
python3 generate.py
```

## Editing the dataset

Edit `initializer.template.json` (the source of truth), then run `python3 generate.py` to refresh `initializer.json`.
Both files are committed.

### Date token grammar

| Token               | Meaning                           | Example output (today = 2026-05-22)          |
|---------------------|-----------------------------------|----------------------------------------------|
| `{{T@HH:MM:SS}}`    | Today UTC at HH:MM:SS             | `2026-05-22T06:05:00Z`                       |
| `{{T+Nd@HH:MM:SS}}` | Today + N days at HH:MM:SS        | `{{T+1d@14:00:00}}` → `2026-05-23T14:00:00Z` |
| `{{T-Nd@HH:MM:SS}}` | Today - N days at HH:MM:SS        | `{{T-3d@06:05:00}}` → `2026-05-19T06:05:00Z` |
| `{{NOW}}`           | Current instant, second precision | `2026-05-22T10:14:23Z`                       |
| `{{TODAY}}`         | Today UTC midnight                | `2026-05-22T00:00:00Z`                       |

The anchor in the template is "today" = the most recent campaign in the demo (`CMP-2026-BFCM-PEAK`). Past campaigns use
`T-Nd`, the future scheduled FHIR campaign uses `T+Nd`.
