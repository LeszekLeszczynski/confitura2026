#!/bin/zsh
# Usage: ./run.sh [-p|--preview] <DemoClassSimpleName> [jvm flags...]
#   ./run.sh ObjectHeader
#   ./run.sh -p ObjectHeader                         # compile + run with --enable-preview
#   ./run.sh ObjectHeader -XX:-UseCompactObjectHeaders
set -e
cd "$(dirname "$0")"

mode=plain
profile=()
if [[ "$1" == "-p" || "$1" == "--preview" ]]; then
  mode=preview
  profile=(-Ppreview)
  shift
fi

if [[ -z "$1" ]]; then
  echo "usage: $0 [-p|--preview] <DemoClass> [jvm flags...]" >&2
  echo "available demos:" >&2
  find src/main/java src/preview/java -name '*.java' -path '*/demo*' \
    | sed 's#src/main/java/.*/#  #; s#src/preview/java/.*/#  (-p) #; s#\.java$##' | sort >&2
  exit 1
fi

name=$1; shift
file=$(find src/main/java src/preview/java -name "$name.java" | head -1)
if [[ -z "$file" ]]; then
  echo "no demo named $name" >&2
  exit 1
fi
fqcn=$(sed -n 's/^package \(.*\);/\1/p' "$file").$name

if [[ "$file" == src/preview/* && $mode != preview ]]; then
  echo "note: $name lives in src/preview - enabling --enable-preview" >&2
  mode=preview
  profile=(-Ppreview)
fi

# Maven's incremental compile ignores changed compiler flags, so rebuild from scratch on a mode switch
goals=(compile)
marker=target/.build-mode
if [[ "$(cat $marker 2>/dev/null)" != "$mode" ]]; then
  goals=(clean compile)
fi

mvn -q "${profile[@]}" "${goals[@]}"
mkdir -p target && echo "$mode" > "$marker"
exec mvn -q "${profile[@]}" exec:exec -Ddemo="$fqcn" -Djvm.args="$*"
