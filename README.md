# Manes Client

Minecraft Bedrock proxy client for Android — one file, four modules, no nonsense.

## What's inside

```
Manes/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/project/manes/
│   │   └── Manes.kt          ← ENTIRE app in one file
│   └── res/values/strings.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## How to build

1. Open this folder in **Android Studio Hedgehog (2023.1.1)** or newer
2. Wait for Gradle sync to finish
3. Plug in your Android phone (Android 9+, USB debugging on)
4. Press **Run ▶**

## How it works

Manes runs a local RakNet proxy on `0.0.0.0:19132`.
You pick a server → tap Launch → Manes starts the proxy and deep-links
Minecraft to connect to `127.0.0.1:19132`.
All packets flow through the module pipeline before being forwarded.

## Modules

| Module     | Category | What it does |
|------------|----------|--------------|
| XRay       | World    | Strips non-ore blocks from chunk data server-side |
| Fullbright | Visual   | Clears fog stack packets so the world is fully lit |
| ESP        | Visual   | Sets the glowing flag on all AddPlayer/AddEntity packets |
| Hitbox     | Combat   | Expands bounding box metadata on entity spawn packets |

Toggle modules on the **Modules** tab before launching.

## Adding your own module

1. Add a new class in `Manes.kt` extending `Module`
2. Override `onClientBound` or `onServerBound`
3. Add an instance to `Modules.all`

## Notes

- Requires Minecraft Bedrock installed on the same device
- Some servers with anti-cheat may detect hitbox/ESP changes
- XRay works purely on chunk data; reconnect to reload chunks
