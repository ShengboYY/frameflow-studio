# Demo media provenance

On 2026-10-31 the two template covers, one source cartoon illustration, two source travel photographs and two matching
results were created with the built-in image_gen tool for FrameFlow Studio.
The portrait uses an original illustrated fox with teal glasses, a mint shirt and yellow overalls;
no realistic person is used. The locations are fictional AI-generated scenes, not user uploads or
photographs downloaded from a third party. Full prompts and input roles are in
[PROMPTS.md](PROMPTS.md).

The blank covers were generated as reusable layouts. Each finished result was then
created by editing its cover with the corresponding source illustration/photographs as image
references, preserving the layout, typography and recognizable subjects. These are
curated visual examples: the application's Mock generator selects a fixed result
by template ID; it does **not** generate from the current user's uploaded content.

| File | Purpose | Access |
| --- | --- | --- |
| portrait-cover.jpg | Blank portrait layout, 640 × 400 JPEG | Public key `portrait-cover` |
| postcard-cover.jpg | Blank two-photo postcard layout, 640 × 400 JPEG | Public key `postcard-cover` |
| portrait-result.jpg | Original cartoon fox applied to portrait layout, 640 × 400 JPEG | Internal `demo:portrait-result`; public illustrative copy in frontend |
| postcard-result.jpg | Both travel sources applied to postcard layout, 640 × 400 JPEG | Internal `demo:postcard-result`; public illustrative copy in frontend |
| mock-result.jpg | Original geometric success fixture, unchanged, 640 × 400 JPEG | Legacy internal `demo:mock-result` only |

`frontend/public/demo-examples/` contains `portrait-source.jpg`,
`travel-coast-source.jpg` and `travel-town-source.jpg` (1200 × 800 JPEG), plus exact
copies of the two current result JPEGs. The template detail's expandable comparison
shows and downloads the appropriate originals, alongside the result and a Mock
notice. These public files contain no user data; actual task result endpoints
retain their existing ownership checks.

The legacy geometric fixture was originally drawn with Java2D and dedicated to the
public domain under CC0-1.0. It is retained for existing successful task references;
new tasks use only the two template-specific references. No database rewrite or
historical regeneration is performed.

To reproduce the artwork, use the prompts in PROMPTS.md with image_gen (generate
three sources and two blank covers, then edit each cover using its source images).
Generation is nondeterministic. Package the resulting seven images from `backend/`:

```sh
java -Djava.awt.headless=true scripts/GenerateDemoMedia.java \
  /path/to/portrait-source.png /path/to/travel-coast-source.png /path/to/travel-town-source.png \
  /path/to/portrait-cover.png /path/to/postcard-cover.png \
  /path/to/portrait-result.png /path/to/postcard-result.png
```

The script only resizes/encodes the generated artwork as JPEG and copies the two
results to the frontend; normal builds do not invoke it. Keep this explicit input
order. It does not overwrite the legacy fixture.

The public Java demo-media endpoint still serves only the two registered covers;
internal result references and arbitrary paths are never public keys.
