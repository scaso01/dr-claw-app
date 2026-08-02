# Third-party notices

Dr. CLAW is licensed under GPL-3.0-or-later (see [LICENSE](LICENSE)). It also
redistributes the third-party components below, each under its own licence.

## sherpa-onnx

- Files: `app/libs/sherpa-onnx-1.12.31.aar`
- Upstream: https://github.com/k2-fsa/sherpa-onnx
- Licence: Apache-2.0

Provides the on-device text-to-speech runtime. The `.aar` is taken unmodified
from an upstream release and contains prebuilt native libraries for
`arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`.

## ONNX Runtime

- Files: `libonnxruntime.so` inside `app/libs/sherpa-onnx-1.12.31.aar`
- Upstream: https://github.com/microsoft/onnxruntime
- Licence: MIT

Bundled by sherpa-onnx as its inference backend.

## Voice bundle: vits-piper-en_US-lessac-medium

Everything under `app/src/main/assets/voices/` is taken verbatim from a single
upstream archive, `vits-piper-en_US-lessac-medium.tar.bz2`, published in the
`tts-models` release of https://github.com/k2-fsa/sherpa-onnx. That archive
repackages two separately licensed works:

### eSpeak NG data

- Files: `app/src/main/assets/voices/espeak-ng-data/` (355 files)
- Upstream: https://github.com/espeak-ng/espeak-ng
- Licence: GPL-3.0-or-later

Supplies the phonemisation dictionaries that turn text into phonemes before
synthesis. This is why Dr. CLAW is distributed under GPL-3.0-or-later: eSpeak NG
is copyleft, and the app ships its data and links the native code that uses it.

### Piper voice model

- Files: `app/src/main/assets/voices/en_US-lessac-medium.onnx`,
  `app/src/main/assets/voices/tokens.txt`
- Piper project: https://github.com/rhasspy/piper
- Voice catalogue: https://huggingface.co/rhasspy/piper-voices
- Licence: MIT

Trained on the Lessac corpus from the Blizzard Challenge 2013. The corpus itself
carries separate terms, documented at
https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html

Note that the model file here is the sherpa-onnx repackaging, which is not
byte-identical to the file of the same name in the Hugging Face catalogue. Verify
this copy against the archive above, not against Hugging Face.

## Gradle wrapper

- Files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Upstream: https://github.com/gradle/gradle
- Licence: Apache-2.0

The wrapper jar is the stock Gradle 9.1.0 artifact and matches the checksum
Gradle publishes at
https://services.gradle.org/distributions/gradle-9.1.0-wrapper.jar.sha256

## Verifying these files

Every vendored artifact here is byte-identical to its upstream release, so you do
not have to take that on trust:

```bash
sha256sum app/libs/sherpa-onnx-1.12.31.aar
# dceb3df5037f2bc581d0f8cad8dd31a65eea73f5940f52795e86cdbeb753f4bc
#   matches sherpa-onnx-1.12.31.aar from the v1.12.31 release of k2-fsa/sherpa-onnx

sha256sum app/src/main/assets/voices/en_US-lessac-medium.onnx
# 4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f
#   matches the model inside vits-piper-en_US-lessac-medium.tar.bz2
```

Declared dependencies resolved at build time are not listed here. Their licences
are recorded in their own artifacts and in `app/build.gradle.kts`.
