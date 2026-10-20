#!/usr/bin/env bash
# Runs Gradle; on failure, republishes the interesting log lines as a single workflow annotation
# so the cause is visible without downloading raw logs.
set +e
./gradlew --no-daemon --console=plain "$@" 2>&1 | tee gradle.log
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ]; then
  summary=$(grep -E "^e: |^w: .*(error)|FAILED|What went wrong|Caused by|Execution failed|Could not|AssertionError|expected:|Unresolved|error:" -A3 gradle.log | head -c 6000)
  summary=${summary//'%'/'%25'}
  summary=${summary//$'\r'/}
  summary=${summary//$'\n'/'%0A'}
  echo "::error title=Gradle $*::${summary}"
fi
exit "$code"
