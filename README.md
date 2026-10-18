# A+

Android study-app foundation: an edge-to-edge Compose screen with the supplied dark-blob SVG at 50% opacity over #262626, a black Attention+ header, a green primary Home action, a #1A8CFF navigation accent, an expandable glass menu, and three navigation tabs. Home follows the supplied four-section sketch; Focus is a placeholder destination; Profile follows the supplied avatar, identity, statistics, subject selector, and daily-points graph reference.

## Glass navigation

Uses [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), published as `io.github.kyant0:backdrop:2.0.1` on Maven Central. The implementation follows its [backdrop documentation](https://kyant.gitbook.io/backdrop/api/backdrops) and [glass bottom-bar tutorial](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar).

`APlusScreen` captures the full page using `rememberLayerBackdrop` and `layerBackdrop`. Home frost samples a separate scenery backdrop so it cannot capture itself. Navigation samples the actual rendered page. The top menu samples a separate header backdrop containing its black background and the fading original logo, without capturing the menu itself. The bar surface and actual icon row are captured separately. The single selected lens combines page and bar backdrops, then samples the icon capture through a separate, shallower AGSL depth/dispersion pass. It is drawn after the original row, so icon pixels visibly refract as the lens moves across them while strong scenery refraction does not copy icon strokes onto the rims. Both optical passes use full-resolution hardware effects. Icon semantics and the existing pointer handler remain on the original row, with exactly three accessible tabs.

`ClearLiquidGlass` shares native, full-resolution blur and real AGSL depth/dispersion lensing across the bar, selected bubble, and top menu. The previous 24–32% white fills and broad white inner shadows are replaced by 1.5–3.5% fills, thin directional highlights, and restrained dark inner shadows. Small blur radii preserve recognizable detail rather than frosting it; there is no rendering-quality reduction.

The selection lens retains its persistent `Animatable` and shared spring response. One parent pointer handler owns tapping and holding, with accessible tab semantics and keyboard actions on the three child targets. Quick taps select on release. Pressing an in-flight lens stops its current spring without jumping. Holding the current lens for 180 ms enters direct drag mode: an absolute grab offset keeps it under the finger, even after dragging past the edges and returning. Selection changes only on release, to the nearest tab, then a velocity-aware spring snaps from that exact release position. Both position bounds and clipping constrain the deformed lens to the inner capsule. Cancellation returns to the selected tab.

The reference is in `References/Menu`. The bar is 72 dp tall, has a 360 dp maximum width, and sits 16 dp above the safe bottom inset. Only three icons are visible.

Android 13/API 33 is the minimum supported version, ensuring AGSL shader refraction is available throughout the supported range. `IMPORTANT/Background/blob-scene-haikei 3.svg` supplies the global scenery. Its six filled paths and group translations are preserved in `drawable/blob_scene_haikei.xml`, scaled uniformly with center cropping. The image is composited at exactly 0.5 alpha over #262626 across all three destinations. The earlier diagnostic linework has been removed. The native window/splash backgrounds also use #262626, with light system-bar icons.

## Home

Home remains the first/leftmost navigation tab and the fresh-launch default. Four compact rectangular cards follow the new sketch: Start Learning / Previous Sessions in the top row, Daily Challenge / Inventory below. Matching card dimensions use a 1.08 height-to-width ratio (with a 160 dp minimum height for readable content on narrow screens), with a 420 dp scene maximum, 36 dp column gap, and 32 dp row gap. Small offsets and different fixed tilts (-6°, +5°, +4°, -7°) create the scattered composition while keeping every card separated. Home scrolls on short displays; navigation remains fixed.

The supplied transparent graduation-cap PNG is copied unchanged to `drawable-nodpi/graduation_cap.png`, displayed with high-quality filtering and a 180-degree rotation. The cap is drawn in front of the entrance origin. Four springs scale and move the cards from the same opening, started in order about 55 ms apart using frame-clock timing. A saved screen-level first-presentation flag is claimed immediately; returning Home or recreating the activity shows fully emerged cards, including if the original entrance was interrupted. A shared six-second sinusoidal phase gives the cap a 3 dp hover and each card an independently phased 1.8 dp hover, without large bouncing.

`FrostedGlass` retains real full-resolution 16 dp backdrop blur, mild edge lensing, and rounded 8 dp corners. A stronger soft shadow adds separation and depth. Start Learning keeps the green #229653 frosted tint, existing spring press, and Focus destination, with a restrained green glow behind the surface. Previous Sessions keeps its disabled Continue action until a resumable session exists. Challenge/reward text and empty Inventory slots remain placeholders.

The entire Inventory card retains its earlier faint square grid, clipped within the rotated surface. Every row moves left to right at about 8.3 dp/second. Its six-second, one-pitch cycle wraps seamlessly; the hover phase has the same period. Text stays above the grid.

## Profile

The person tab opens Profile through either tapping or drag release. Its layout preserves the reference hierarchy: a circular avatar at left; username, handle, all-time points, and streak at right; then a three-column subject summary above a framed daily-points graph. The entire Profile area below the header is a continuous, darker frosted background, using real 24 dp clamped native backdrop blur and restrained dark tint. The profile and Subject summary share this surface without enclosing cards or borders; the phone outline from the reference is not recreated. Only the avatar and graph retain their own frames. The page scrolls with navigation always visible.

`ProfileSummary` / `ProfileSubject` are replaceable presentation models. Since the project has no account or study-history source, the initial username/handle and three subjects are placeholders, totals are zero, and the graph explicitly shows “No points yet.” Supplying daily points draws the corresponding series. Subject choice, scroll position, page selection, and menu expansion survive navigation and activity recreation.

## Expandable glass menu

`ExpandableGlassMenu` is a single persistent `drawBackdrop` surface. Its width grows from a 56 dp circle into a capsule and retracts back to that circle. A shared spring response drives width with a restrained vertical swell during the stretch; a slightly faster spring leads the two-stroke chevron's quarter turn. Placeholder targets marked 01 and 02 are revealed by the growing shape and intentionally perform no action. The left circular target remains clickable throughout, including during interrupted animations.

Pressing that target compresses the complete glass object about the circle's center and slightly increases refraction. `AppHeader` is completely black, including behind the status bar, with an 88 dp content zone and a thin #292929 divider. The existing circle begins 16 dp below the safe top inset. The uploaded Attention+ PNG is copied unchanged to `drawable-nodpi/attention_logo.png`; `BitmapPainter` excludes its empty margins at display time and uses high-quality filtering. A transparent cutout was attempted with the built-in imagegen background-extraction tool, but its edge artifacts failed visual review, so the original artwork uses the requested seamless black-header fallback. The large logo is horizontally centered with room for the collapsed circle. The opening logo fade uses a faster spring (stiffness 1800) so it clears the growing menu early; its return keeps the usual stiffness 620. Both remain smooth and preserve current values on interruption. Home/Profile content begins 20 dp below the header. Menu expansion and tab selection survive activity recreation. `GlassMotion` shares the restrained spring response across menu reveal, press release, navigation travel, and icon tint.

Effects follow the library's documented vibrancy → blur → lens order. Backdrop 2.0.1 exposes native blur radius and lens height/amount, depth, and dispersion; it does not expose a render-resolution, sampling-quality, or frame-interpolation switch. No additional quality reduction or shader fallback is introduced.

## Build and run

Open in Android Studio, sync Gradle, and run the `app` configuration. The existing AGP 9.4.1 and SDK 37 configuration is preserved. AGP's built-in Kotlin is pinned to 2.4.10 to match the library; the Compose compiler plugin uses the same version.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

With an Android emulator or device connected:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The Compose interaction tests cover three tabs, a single lens, intermediate slide positions, menu growth/retraction, physical press compression, coordinated interruptions, new half-opacity dark SVG colors and exact navigation accent, black header, centered logo, coordinated reversible logo fading, quiet divider, green CTA, continuous darker Profile frost and absent enclosing profile/Subject frames, actual quick taps, continuous bounded dragging after short holds (including native Android MotionEvent injection), nearest-tab and same-tab release, profile navigation, subject choice, and state restoration. Home checks cover layout hierarchy, fresh selection/order, the Start Learning destination, disabled Continue, background across all destinations, inventory direction/speed sampled in its rotated local axes, clipping, and seamless wrapping; cap-origin stagger, upside-down asset rendering, hovering, spacing, and entrance persistence across revisits and recreation. A hardware-rendered icon test verifies that the selected icon silhouette changes while the moving lens passes it without changing page selection. A hardware-rendered diagnostic stripe test samples interior pixels away from rims and stripe boundaries to distinguish real content displacement from simple tint/blur/highlights, including while dragging and during menu expansion/retraction. Espresso remains 3.7.0; application toolchain versions are unchanged.

Latest local verification: debug app and instrumentation APK builds, unit tests, and lint passed (zero lint errors). All 26 AndroidJUnitRunner tests passed on the API 37 emulator, including actual glass refraction, native drag gestures, cap-origin stagger, floating motion, no replay on Home return/activity recreation, and the faster logo fade. Final preview: `.gradle/cap-home-final.png`; motion demo: `.gradle/cap-home-demo.mp4`.
