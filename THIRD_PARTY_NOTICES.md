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

## eSpeak NG

- Files: `app/src/main/assets/voices/espeak-ng-data/`
- Upstream: https://github.com/espeak-ng/espeak-ng
- Licence: GPL-3.0-or-later

Supplies the phonemisation dictionaries used to turn text into phonemes before
synthesis. This is why Dr. CLAW is distributed under GPL-3.0-or-later: eSpeak NG
is copyleft, and the app ships its data and links the native code that uses it.

## Piper voice: en_US-lessac-medium

- Files: `app/src/main/assets/voices/en_US-lessac-medium.onnx`,
  `app/src/main/assets/voices/tokens.txt`
- Upstream: https://huggingface.co/rhasspy/piper-voices
- Piper project: https://github.com/rhasspy/piper
- Licence: MIT

Trained on the Lessac corpus from the Blizzard Challenge 2013. The corpus itself
carries separate terms, documented at
https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html

## Gradle wrapper

- Files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Upstream: https://github.com/gradle/gradle
- Licence: Apache-2.0

Declared dependencies resolved at build time are not listed here. Their licences
are recorded in their own artifacts and in `app/build.gradle.kts`.
