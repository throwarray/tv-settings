# Third-party code

## Stickman (MIT)
Copyright (c) 2026 Mohamed Nofal. https://github.com/MohamedNofal/stickman

Credit for the button remapping daemon this project builds on.

- `third_party/stickman/native/` — `remapd.c`, `layers.c`, `layers.h`, `amcheck.h`, unchanged from upstream.
- `third_party/stickman/LICENSE` — the MIT license text, kept with the copied sources.
- `app/src/main/jniLibs/armeabi-v7a/libremapd.so` — `remapd` built from those sources for armv7 (static).
- `app/src/main/java/com/clean/stickman/daemon/Injector.java` — TVSettings's own implementation of the
  injector protocol `remapd.c` uses. Not a copy of Stickman's injector.

