# Sports Hub

An Android sports app: live scores and stats, plus a Watch tab for video sources you add yourself. NFL first, with more sports planned.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer) for playback, OkHttp for networking
- Min Android 8.0 (API 26), target API 35

## Structure

```
app/src/main/java/com/sportshub/app/
  model/      Sport, Game, Team, StreamSource (sport-agnostic)
  data/       ScoresRepository (swappable data provider), SourcesRepository (saved URLs)
  ui/         Scores, Watch and Sources tabs, shared components, theme
```

Adding a sport means flipping `isAvailable` in `Sport.kt` and teaching the scores provider about it. The UI reads from the enum.

## Sources

The app ships with no video sources. On the Sources tab you paste a page URL, and later builds scan that page for standard video (files, HLS streams, embeds). Only add pages you have the rights to watch.

## Building

Open in Android Studio, or run `./gradlew assembleDebug`. Every push builds a debug APK in GitHub Actions; download it from the run's artifacts.
