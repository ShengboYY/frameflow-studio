# Demo media provenance

Created for FrameFlow Studio on 2026-10-31 using the original Java2D drawing instructions
in `backend/scripts/GenerateDemoMedia.java`, written with Codex assistance during IP-05.
No photographs, downloaded assets, fonts, logos, or third-party artwork are included.
The geometric drawings and their generator are dedicated to the public domain under
CC0-1.0. They are demonstration illustrations, not outputs made from uploaded images.

| File | Purpose | Access |
| --- | --- | --- |
| portrait-cover.png | Portrait card cover, 640 × 400 PNG | Public key `portrait-cover` |
| postcard-cover.png | Travel postcard cover, 640 × 400 PNG | Public key `postcard-cover` |
| mock-result.png | Fixed mock success fixture, 640 × 400 PNG | Internal reference `demo:mock-result` |

Regenerate from `backend/` with Java 17:

```sh
java -Djava.awt.headless=true scripts/GenerateDemoMedia.java
```

The public demo-media endpoint only serves the two registered covers. The fixture is
available to trusted backend Cases; it has no public demo-media key.
