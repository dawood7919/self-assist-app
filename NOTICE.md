# Licensing

Orbit is licensed under the **GNU General Public License, version 3 or later**.
The full text is in [`LICENSE`](LICENSE).

## Why GPL

The Video Downloader uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
to read stream URLs from sites that sign them per session. NewPipeExtractor is
GPL-3.0, and the GPL applies to the whole of a work that links against it — so
Orbit is GPL-3.0 too.

In practice that means: anyone you give this app to is entitled to its source,
under the same licence. That is fine for a personal build and for publishing
the source, and it is worth knowing before putting it anywhere that expects a
proprietary licence.

## Third-party components

| Component | Licence | Used for |
|---|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 | Reading stream URLs from signed sites |
| [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) | Apache-2.0 | PDF merge, split, compress, watermark, text |
| [ML Kit Text Recognition](https://developers.google.com/ml-kit) | Google ML Kit Terms | On-device OCR |
| [ZXing Core](https://github.com/zxing/zxing) | Apache-2.0 | QR and barcode encode/decode |
| [AndroidX Media3](https://github.com/androidx/media) | Apache-2.0 | Video playback |
| [Coil](https://github.com/coil-kt/coil) | Apache-2.0 | Image and video-frame loading |
| [OkHttp](https://square.github.io/okhttp/) | Apache-2.0 | HTTP |
| [jsoup](https://jsoup.org/) | MIT | HTML parsing |

## Signing key

`app/orbit-ci.jks` is committed so every build carries the same signature and
installs over the last one. It is therefore **public**: anyone can sign an APK
that a device would accept as an update to Orbit. Replace it with a key you
keep private before distributing this app to anyone else.
