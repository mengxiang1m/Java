#!/usr/bin/env bash
set -euo pipefail

repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd "$repo"

is_jdk25() {
    [[ -x "$1/bin/javac" ]] && [[ $("$1/bin/javac" -version 2>&1) == 'javac 25'* ]]
}

if [[ -z ${JAVA_HOME:-} ]] || ! is_jdk25 "$JAVA_HOME"; then
    for candidate in "$repo/../.tools/idea-linux"/*/jbr \
            "$HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr"; do
        if is_jdk25 "$candidate"; then export JAVA_HOME="$candidate"; break; fi
    done
fi
if [[ -z ${JAVA_HOME:-} ]] || ! is_jdk25 "$JAVA_HOME"; then
    printf '%s\n' 'A full JDK 25 is required. Set JAVA_HOME to its installation directory.' >&2
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Reuse this workstation's existing cache; normal clones keep Gradle's default cache.
cached_home="${repo%%/Documents/*}/.gradle"
if [[ -z ${GRADLE_USER_HOME:-} && -d "$cached_home/wrapper/dists/gradle-9.6.0-bin" ]]; then
    export GRADLE_USER_HOME="$cached_home"
fi

ide_args=()
explicit_ide=false
for argument in "$@"; do
    if [[ $argument == -PlocalIdePath=* ]]; then explicit_ide=true; fi
done
if [[ $explicit_ide == false ]]; then
    idea_path=${CST3492_IDEA_HOME:-}
    if [[ -z $idea_path ]]; then
        for candidate in "$repo/../.tools/idea-linux"/*/product-info.json; do
            [[ -f $candidate ]] || continue
            if python3 - "$candidate" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
sys.exit(0 if d.get('productCode') in ('IU','IC') and d.get('buildNumber','').startswith('262.') else 1)
PY
            then idea_path=${candidate%/product-info.json}; break; fi
        done
    fi
    if [[ -n $idea_path ]]; then ide_args=("-PlocalIdePath=$idea_path"); fi
fi

exec bash "$repo/gradlew" "${ide_args[@]}" "$@"
