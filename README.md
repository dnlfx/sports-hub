# Sports Hub

An Android sports app: live scores and stats, team pages, player profiles, plus a Watch tab for video sources you add yourself. NFL, college football and NBA, with more sports planned. Switch sports from the chip in the top bar; the app remembers your pick.

## Features

- **Scores**: live, upcoming and final games, by week for the NFL and college football and by day for the NBA (a strip of the season's game days). Tap a game for its box score.
- **Your teams**: follow teams with the star on a team page, or from the card on Scores. Their games lead the Scores tab under "Your teams", with a strip showing each team's week at a glance (live score, result, kickoff or bye). Saved on the device only; no notifications.
- **Game page**: score header in both teams' colors, scoring by quarter, a win probability chart, player stats per team (passing, rushing, receiving, defense, kicking; interceptions and fumbles are folded into those tables), and team stats.
- **Win probability**: each team's chance play by play, shaded in team colors, with the plays that swung the game marked. Tap a marker or drag across the chart to read any play. Before kickoff (or tip-off), ESPN's matchup predictor shows instead.
- **NBA games**: scoring by quarter and overtime, the box score split into starters and bench (minutes, points, rebounds and assists up front), team stats, the win probability chart with 3-pointers, dunks and turnovers marked, and the last play while live.
- **College football**: the top of the sport only: the SEC, Big Ten, Big 12 and ACC, plus Notre Dame. A game shows when at least one side is one of those teams, so Ohio State against a smaller school is listed but two smaller schools aren't. Ranked teams carry their poll rank on score cards and game pages. Bowls and the playoff follow week 15 (conference championships). Teams are grouped by conference.
- **Teams**: the standings, a card per division (per conference in college football) with each team's rank, record, games behind and streak, the leader marked. College conferences rank by conference record. Your teams lead the page with their place in their division, and pills narrow it to one conference (AFC or NFC, East or West). A team page has its schedule (tap any game, past or future), player season stats added up from each game's box score (per-game averages and totals in the NBA), team season stats, the roster and the depth chart.
- **Players**: tap a player anywhere for their bio (height, weight, age, college, draft) right under their name and photo, this season's numbers and career stats by year. Only the stat categories that fit their position show, unless they really play both ways.
- **TV coverage (NFL)**: a map for each network and window of the week (CBS early, FOX single game, Sunday night and so on), colored by the game each part of the country gets, with each game's share of TV homes and the user's own market ringed. Tap the map to see what any market gets; arrows step through the weeks. It opens from the card at the top of the NFL scores. The coverage is projected the way the networks split the country: a team's home markets (within 75 miles of its stadium, plus Milwaukee for Green Bay) always get its game, and so does its fan region (`data/tv/NflRegions.kt`); a market whose team isn't playing gets a division rival's game from nearby; everywhere else gets the best mix of a near game and a big draw (the teams' national following and records), so the featured game reaches farthest. A lone game on its network goes everywhere, and cable or streaming games (ESPN, Prime Video, NFL Network, Netflix) are free over the air only in the teams' home markets. The network with the Sunday doubleheader is the one with 4:25 PM kickoffs; the other network's early and late games share one "single game" map, where a market whose team plays early on the other network gets a late game. Checked against the real antenna listings of about 60 cities: 91% of market maps right in the week the rules were tuned on, 73% in a week they weren't (`CoverageAccuracyTest`). A link opens that week's maps at 506 Sports.
- **On free TV near you**: games the user gets with an antenna show their network with an antenna icon on score cards. Set the TV market from the coverage page, from the phone's approximate location (asked for only then, and used once) or by searching about 200 markets. With a ZIP code (typed in the market picker, or found with the location), NFL cards show the real local channel ("CBS 2.1") from the free TV guide at tvlistings.gracenote.com: its antenna lineup for that ZIP, one request per six hours of kickoffs, kept for half an hour. In Dallas-Fort Worth, where every ZIP gets the same stations, the market alone is enough (the app asks with 75201). The guide only answers web browsers, so the app asks the way its web page does, twice before giving up. The football part of each answer is saved on the phone, and stands in when the guide refuses later; games it never answered for, and games it lists only as "NFL Football" without teams (before the networks place the week's games), keep the estimate above. Without a market, only national broadcast games are marked. Other sports mark broadcast games that are alone on their network at the time.
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
  data/tv/    TV markets, NFL home markets, coverage windows, the map outline and the user's market
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
- Large screens: game cards keep a readable width in the middle, and the Teams standings sit side by side on tablets and foldables, with more columns (win percentage, division record) when a card has room.
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

Targets Android 17 (API 37), minimum Android 8.0 (API 26). On Android 17 the app asks for local network access only if a saved source is on the home network (a private IP or a `.local` name). Approximate location is asked for only when the user taps "Use my location" to find their TV market.

The coverage map's outline (`app/src/main/assets/us_map.txt`) is built from the U.S. Census Bureau's state boundaries, via the us-atlas package, by `tools/make_us_map.py`.

## Sources

The app ships with no video sources. On the Sources tab you paste a page URL, and later builds scan that page for standard video (files, HLS streams, embeds). Only add pages you have the rights to watch.

## Building

Open in Android Studio, or run `./gradlew assembleDebug`. Pull requests build a debug APK in GitHub Actions.

Screenshots of the real app, with live scores and listings, render on the JVM with Robolectric (no emulator): `./gradlew testDebugUnitTest -Pscreenshots --tests '*AppScreenshots*'` writes PNGs of the NFL scores and coverage pages to `app/build/screenshots`, for Dallas-Fort Worth unless `-Pscreenshots.market=new-york -Pscreenshots.zip=10001 -Pscreenshots.tz=America/New_York` say otherwise. They're skipped in normal test runs. Every merge to `main` publishes a single release, named like **Sports Hub 1.0.12 (latest)**, with the installable APK attached; older releases are removed.

## Release signing

Releases are signed in GitHub Actions with the key in these repository secrets:
`RELEASE_KEYSTORE_BASE64` (the keystore file, base64), `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_ALIAS`.
If the secrets are missing, the release is signed with the debug key and the workflow shows a warning.
Keep a backup of the keystore: without it, new releases can't update installed copies.
