# Creator Pulse

Repository: https://github.com/ArtyomAlekseyevich/creator-pulse · Releases (APKs): https://github.com/ArtyomAlekseyevich/creator-pulse/releases

An unofficial Android companion for the Roblox Creator Hub: live player counts, visits,
Robux balance, sales and group revenue, Open Cloud analytics charts, and two home screen widgets.

## Features

- **Experiences tab** – every experience you own (plus tracked groups' and any added by id) with
  players online, visits, favorites, like ratio, and, with an API key, latest DAU / 7-day revenue /
  Robux spent today.
- **Experience detail** – concurrent players (24h), realtime Robux spent (24h), DAU, revenue, visits,
  paying users, playtime, session length, D1/D7 retention over 7 / 30 / 90 days. Buttons to open the
  Creator Hub page and to pin a widget for that experience.
- **Revenue tab** – Robux balance, transaction totals (Day / Week / Month / Year) for you and for each
  tracked group, full breakdown (sales, premium payouts, pending, DevEx, …) and recent sales.
- **Widgets**
  - *Experience stats* – one experience; resizable from 2x1 (players online) up to 4x3 (adds revenue,
    spent today and a DAU sparkline). Tap to open the experience, tap the arrow icon to refresh now.
  - *Creator overview* – Robux, players online, sales today and this month; taller sizes list your top
    experiences.
- Background refresh every 15 min to 3 h (WorkManager), survives reboots.
- **Themes** (Settings → Appearance): system / light / dark mode, Material You wallpaper colors or
  nine accent presets, a custom accent (hue, vibrance or hex), pure-black OLED dark mode,
  accent-colored charts, and widget background opacity. Widgets use the same palette as the app.

## Staying under Roblox's rate limits

- Analytics queries go through a per-key limiter (20 per minute, Roblox allows ~30) and every
  result is cached on disk: hourly/realtime series for 10 minutes, daily series for 2 hours (24 h once
  the range is fully in the past). Reopening a screen or re-rendering a widget costs nothing.
- On a 429 the app waits for Roblox's `Retry-After` and retries once; if data is still unavailable it
  shows the last cached copy with a note instead of an error.
- Web requests (games, economy) share a 90/min limiter, icons are refetched at most every 6 h, and
  Week / Year revenue totals are only loaded when you select them.

## Ads

- **Experience page → Ads switch** loads the publisher ad metrics from the Analytics API: total ad
  revenue and impressions, rewarded video earnings, impressions, EPM and unique viewers.
- **Experiences tab** shows a 7-day ad revenue tile and per-experience "ads R$…" when it is non-zero.
- **Revenue tab** shows *Ad payouts* (immersive / rewarded video revenue share) and *Ad spend*
  (Ads Manager / sponsoring) for the selected time frame.
- **Ads & web tab** wraps the Roblox website with your session for everything that has no read API:
  it opens **Ads Manager** (campaigns, sponsorships, reports) by default, with one-tap links to the
  Creator Hub, Transactions and group payouts, plus back/forward/reload, a desktop/mobile site toggle
  and "open in browser". Signing in there also signs the app in. The experience page's *Creator Hub*
  button opens that experience's dashboard in the same tab.

## Data sources

| What | Endpoint | Auth |
|---|---|---|
| Live players, visits, favorites, votes, icons | `games.roblox.com`, `thumbnails.roblox.com` | none |
| Your experiences and groups | `develop.roblox.com`, `groups.roblox.com` | Roblox session cookie |
| Robux balance, transaction totals, group revenue summary, sales list | `economy.roblox.com` | Roblox session cookie |
| DAU, revenue, retention, playtime, CCU… | Open Cloud **Analytics Query API** (`apis.roblox.com/analytics-query-api`, beta) | Open Cloud API key |

### Signing in

Settings → *Sign in with Roblox* opens the real roblox.com login in a WebView. The app only keeps the
`.ROBLOSECURITY` cookie the site sets, encrypted with the Android Keystore
(`EncryptedSharedPreferences`), and sends it on the same requests the Creator Hub makes. You can also
paste the cookie manually. Sign out wipes it.

### Open Cloud API key (for analytics charts)

1. Creator Hub → **Credentials** → **API keys** → *Create API key*.
2. Under *Access permissions* add the **universe-analytics** system, pick the experiences, and tick
   **universe.analytics:read**.
3. For group-owned experiences create the key with the group as owner.
4. Paste the key in Settings → *Open Cloud API keys*. You can add several keys; the app remembers
   which key works for which experience.

## Updates

The app checks GitHub Releases of this repository once a day (Settings → Updates has a manual check).
When a newer version exists it downloads the APK and opens the Android installer; the first time,
Android asks to allow installs from Creator Pulse.

To ship an update: bump `versionCode` / `versionName` in `app/build.gradle.kts`, commit, then

```bash
git tag v1.2.0 && git push origin main --tags
```

The `Release` workflow builds the signed APK and publishes the release. Signing uses the repository
secrets described in `keystore/README.md`; without them the workflow falls back to the debug key, which
phones with the release-signed app will refuse as an update.

## Building

Requirements: JDK 17+, Android SDK with platform 36 and build-tools 36.

```bash
./gradlew :app:assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r app/build/outputs/apk/debug/app-debug.apk` or copy it to the phone.

A release build (`./gradlew :app:assembleRelease`) is minified and signed with the debug key so it
installs directly; swap in your own signing config in `app/build.gradle.kts` before publishing.

## Project layout

```
app/src/main/java/dev/creatorpulse/
  App.kt                 Application: wires SecureStore / Settings / RobloxApi / Repository, schedules refresh
  MainActivity.kt        Single-activity Compose host; handles widget deep links
  data/
    RobloxApi.kt         HTTP client for the Roblox web APIs and the Analytics Query API
    Repository.kt        Builds the Snapshot (everything the UI and widgets render) and per-metric series
    Models.kt            Serializable models + labels for the revenue breakdown fields
    SecureStore.kt       Encrypted storage for the cookie and API keys
    Settings.kt          DataStore prefs + cached Snapshot JSON
  ui/                    Compose screens (Home, Experience, Revenue, Settings, Login) and charts
  ui/theme/              CreatorPulseTheme + SchemeBuilder (Material 3 scheme generated from one seed color)
  data/ThemeStore.kt     Theme settings (SharedPreferences, synchronous so the first frame is already themed)
  widget/                Glance widgets, config activity, pin receiver, WorkManager refresh
```

## Notes and limits

- Daily analytics metrics are bucketed by UTC day and are finalized by Roblox some hours after midnight
  UTC; "Spent today" uses the realtime `ItemMonetizationRevenue` metric instead.
- The Analytics Query API allows roughly 30 queries per minute per key owner. A background refresh
  queries summaries for at most 8 experiences (widget ones first); the detail screen loads its charts
  two at a time.
- Cookie-based endpoints are "legacy" APIs that Roblox can change without notice. Everything is parsed
  leniently so unknown fields show up with humanized labels rather than breaking.
