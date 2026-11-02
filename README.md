# Sports Hub

An Android sports app: live scores and stats, team pages, player profiles, plus a Watch tab for video sources you add yourself. NFL first, with more sports planned.

## Features

- **Scores**: live, upcoming and final games by week. Tap a game for its box score.
- **Game page**: score header in both teams' colors, scoring by quarter, player stats per team (passing, rushing, receiving, defense, kicking; interceptions and fumbles are folded into those tables), and team stats.
- **Teams**: every team by division. A team page has its schedule (tap any game, past or future), player season stats added up from each game's box score, team season stats, the roster and the depth chart.
- **Players**: tap a player anywhere for their bio (height, weight, age, college, draft), this season's numbers and career stats by year.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer) for playback, OkHttp for networking
- Min Android 8.0 (API 26), target API 35

## Structure

```
app/src/main/java/com/sportshub/app/
  model/      Sport, Game, Team, StreamSource (sport-agnostic)
  data/       ScoresRepository (swappable data provider), SourcesRepository (saved URLs), page scanner
  ui/         Scores, Watch and Sources tabs, game detail, player, shared components, theme
```

## Design

- "Night game" look: an inky base, the app icon's electric blue as the accent and the football's orange for small highlights.
  Material 3 with a full custom color scheme for light and dark mode (`ui/theme/Theme.kt`).
  Colors Material has no role for (cards, borders, live red, win/loss) live in `SportsHubTheme.colors`.
- A floating pill tab bar. Lists add `bottomListPadding()` so their last item scrolls clear of it.
- Game, team and player pages open with a full-width header in the team's colors.
- Barlow Condensed for titles and scores, with tabular figures so digits don't shift; body text uses the system font.
  The font is under the SIL Open Font License (`licenses/BarlowCondensed-OFL.txt`).
- Each tab has a large title that collapses on scroll, with the sport switcher in the top bar.
- Team colors tint score cards and headers, adjusted so very dark or very light colors stay visible.
- No loading flicker: everything already loaded is kept in memory and on disk, so pages draw in full on their first frame and refresh in place. While something loads for the first time, a placeholder of the same shape holds its place.

Adding a sport means flipping `isAvailable` in `Sport.kt` and teaching the scores provider about it. The UI reads from the enum.

## Sources

The app ships with no video sources. On the Sources tab you paste a page URL, and later builds scan that page for standard video (files, HLS streams, embeds). Only add pages you have the rights to watch.

## Building

Open in Android Studio, or run `./gradlew assembleDebug`. Pull requests build a debug APK in GitHub Actions. Every merge to `main` publishes a single release, named like **Sports Hub 1.0.12 (latest)**, with the installable APK attached; older releases are removed.

## Release signing

Releases are signed in GitHub Actions with the key in these repository secrets:
`RELEASE_KEYSTORE_BASE64` (the keystore file, base64), `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_ALIAS`.
If the secrets are missing, the release is signed with the debug key and the workflow shows a warning.
Keep a backup of the keystore: without it, new releases can't update installed copies.
