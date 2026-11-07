# Sports Hub

An Android sports app: live scores and stats, team pages, player profiles, plus a Watch tab for video sources you add yourself. NFL, college football and NBA, with more sports planned. Switch sports from the chip in the top bar; the app remembers your pick.

## Features

- **Scores**: live, upcoming and final games, by week for the NFL and college football and by day for the NBA (a strip of the season's game days). Tap a game for its box score.
- **Your teams**: follow teams with the star on a team page, or from the card on Scores. Their games lead the Scores tab under "Your teams", with a strip showing each team's week at a glance (live score, result, kickoff or bye). Saved on the device only; no notifications.
- **Game page**: score header in both teams' colors, scoring by quarter, a win probability chart, player stats per team (passing, rushing, receiving, defense, kicking; interceptions and fumbles are folded into those tables), and team stats.
- **Win probability**: each team's chance play by play, shaded in team colors, with the plays that swung the game marked. Tap a marker or drag across the chart to read any play. Before kickoff (or tip-off), ESPN's matchup predictor shows instead.
- **NBA games**: scoring by quarter and overtime, the box score split into starters and bench (minutes, points, rebounds and assists up front), team stats, the win probability chart with 3-pointers, dunks and turnovers marked, and the last play while live.
- **College football**: the top of the sport only: the SEC, Big Ten, Big 12 and ACC, plus Notre Dame. A game shows when at least one side is one of those teams, so Ohio State against a smaller school is listed but two smaller schools aren't. Ranked teams carry their poll rank on score cards and game pages. Bowls and the playoff follow week 15 (conference championships). Teams are grouped by conference.
- **Teams**: every team by division, your teams first. A team page has its schedule (tap any game, past or future), player season stats added up from each game's box score (per-game averages and totals in the NBA), team season stats, the roster and the depth chart.
- **Players**: tap a player anywhere for their bio (height, weight, age, college, draft) right under their name and photo, this season's numbers and career stats by year. Only the stat categories that fit their position show, unless they really play both ways.
- **Compare**: two teams or two players side by side, stat by stat, with the better number in each row in that side's color and a tally of who's ahead. Open it from a team or player page, or the Teams tab. Team stats compare per game where ESPN gives it; players compare their latest season or whole career.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer) for playback, OkHttp for networking
- Min Android 8.0 (API 26), target API 35

## Structure

```
app/src/main/java/com/sportshub/app/
  model/      Sport, Game, Team, StreamSource (sport-agnostic)
  data/       ScoresRepository (swappable data provider), TeamRepository, FavoritesRepository (followed teams), SourcesRepository (saved URLs), page scanner
  ui/         Scores, Teams, Watch and Sources tabs, game detail (with the win probability chart), team, player, compare, favorites, shared components, theme
```

## Design

- "Night game" look: an inky base, the app icon's electric blue as the accent and the football's orange for small highlights.
  Material 3 with a full custom color scheme for light and dark mode (`ui/theme/Theme.kt`).
  Colors Material has no role for (cards, borders, live red, win/loss) live in `SportsHubTheme.colors`.
- A tab bar docked to the bottom edge, running under the system navigation bar. Pages sit above it, so nothing scrolls underneath; lists add `bottomListPadding()` for a little room at the end.
- Game, team and player pages open with a full-width header in the team's colors.
- Barlow Condensed for titles and scores, with tabular figures so digits don't shift. Inter for names, labels, stats and body text,
  bundled as one small variable font (Latin, weights 400 to 800). Both are under the SIL Open Font License (`licenses/`).
- Large screens: game cards keep a readable width in the middle, and the Teams grid adds columns on tablets, foldables and in landscape.
- Accessibility: tabs announce their selected state, a game card reads as one sentence ("Live. Bills 24, Jets 17. Q3 4:12."), and team logos next to a team's name stay silent so names aren't read twice.
- Each tab has a large title that collapses on scroll, with the sport switcher in the top bar.
- Team colors tint score cards and headers, adjusted so very dark or very light colors stay visible.
- No loading flicker: everything already loaded is kept in memory and on disk, so pages draw in full on their first frame and refresh in place. While something loads for the first time, a placeholder of the same shape holds its place.

Adding a sport means flipping `isAvailable` in `Sport.kt` and teaching the scores provider about it. The UI reads from the enum. Basketball's box scores and season stats are shaped in `data/BasketballStats.kt`, football's in `data/BoxScore.kt`; divisions live in `ui/team/TeamGroups.kt`. The college conferences and their teams (by ESPN team id) live in `data/CollegeFootball.kt`; a school changing conferences is a one-line move there.

## Performance

- A baseline profile (`app/src/main/baseline-prof.txt`) plus ProfileInstaller, so startup and first scrolls run precompiled code, also for APKs installed from GitHub releases.
- One OkHttp client with an HTTP cache: scores and game details always check with the server; team pages, rosters and player bios reuse fresh copies. Images use Coil's own disk cache.
- R8 with class repackaging, and a Compose stability config (`compose-stability.conf`) so screens skip recomposing when lists haven't changed.
- Network loads let coroutine cancellation through, so a cancelled load never writes a stale error to the screen.

## Platform

Targets Android 17 (API 37), minimum Android 8.0 (API 26). On Android 17 the app asks for local network access only if a saved source is on the home network (a private IP or a `.local` name).

## Sources

The app ships with no video sources. On the Sources tab you paste a page URL, and later builds scan that page for standard video (files, HLS streams, embeds). Only add pages you have the rights to watch.

## Building

Open in Android Studio, or run `./gradlew assembleDebug`. Pull requests build a debug APK in GitHub Actions. Every merge to `main` publishes a single release, named like **Sports Hub 1.0.12 (latest)**, with the installable APK attached; older releases are removed.

## Release signing

Releases are signed in GitHub Actions with the key in these repository secrets:
`RELEASE_KEYSTORE_BASE64` (the keystore file, base64), `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_ALIAS`.
If the secrets are missing, the release is signed with the debug key and the workflow shows a warning.
Keep a backup of the keystore: without it, new releases can't update installed copies.
