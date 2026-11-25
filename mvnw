#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper (only-script flavour) for ShelfLife.
#
# Reads distributionUrl from .mvn/wrapper/maven-wrapper.properties, downloads
# and unpacks that Maven distribution once into ~/.m2/wrapper/dists, then runs
# it with the given arguments. No maven-wrapper.jar is needed.
#
# Optional environment variables:
#   JAVA_HOME          JDK to use (otherwise `java` on PATH)
#   MAVEN_USER_HOME    where distributions are cached (default ~/.m2)
#   MVNW_REPOURL       mirror that replaces https://repo.maven.apache.org/maven2
#   MVNW_USERNAME / MVNW_PASSWORD   basic auth for the download
#   MVNW_VERBOSE=true  print what the wrapper is doing
# ----------------------------------------------------------------------------

set -euf

verbose() { if [ "${MVNW_VERBOSE-}" = true ]; then printf '%s\n' "$*"; fi; }
die() { printf 'mvnw: %s\n' "$*" >&2; exit 1; }

# Find the project base directory: the nearest parent that contains .mvn
find_basedir() {
  dir=$(cd "$(dirname "$0")" && pwd -P)
  while [ "$dir" != / ]; do
    if [ -d "$dir/.mvn" ]; then printf '%s' "$dir"; return 0; fi
    dir=$(dirname "$dir")
  done
  pwd -P
}
MAVEN_PROJECTBASEDIR=$(find_basedir)
export MAVEN_PROJECTBASEDIR

props="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$props" ] || die "cannot find $props"

distributionUrl=
while IFS="=" read -r key value || [ -n "$key" ]; do
  case "${key-}" in
    distributionUrl) distributionUrl=$(printf '%s' "${value-}" | tr -d '\r') ;;
  esac
done <"$props"
[ -n "$distributionUrl" ] || die "distributionUrl is not set in $props"

if [ -n "${MVNW_REPOURL-}" ]; then
  distributionUrl="${MVNW_REPOURL%/}/org/apache/maven/${distributionUrl#*/org/apache/maven/}"
fi

# e.g. apache-maven-3.9.11-bin.zip -> apache-maven-3.9.11
distributionFile=${distributionUrl##*/}
distributionName=${distributionFile%.zip}
distributionName=${distributionName%-bin}

# Cache dir includes a hash of the URL so a changed URL gets a fresh download
hash_string() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | cut -c1-32
  elif command -v shasum >/dev/null 2>&1; then
    printf '%s' "$1" | shasum -a 256 | cut -c1-32
  else
    printf '%s' "$1" | cksum | cut -d' ' -f1
  fi
}
MAVEN_USER_HOME=${MAVEN_USER_HOME:-${HOME}/.m2}
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$distributionName/$(hash_string "$distributionUrl")"

run_maven() {
  verbose "Using Maven at $MAVEN_HOME/$distributionName"
  exec "$MAVEN_HOME/$distributionName/bin/mvn" "$@"
}

if [ -x "$MAVEN_HOME/$distributionName/bin/mvn" ]; then
  run_maven "$@"
fi

verbose "Downloading $distributionUrl"
mkdir -p "$MAVEN_HOME"
tmpDir=$(mktemp -d "$MAVEN_HOME/tmp.XXXXXX")
trap 'rm -rf "$tmpDir"' EXIT INT TERM

# Retry briefly: Maven Central sometimes answers 429 (rate limited) or 5xx for a few seconds.
if command -v curl >/dev/null 2>&1; then
  if [ -n "${MVNW_USERNAME-}" ]; then
    curl -fsSL --retry 4 --retry-delay 5 -u "$MVNW_USERNAME:${MVNW_PASSWORD-}" -o "$tmpDir/$distributionFile" "$distributionUrl" || die "download failed: $distributionUrl"
  else
    curl -fsSL --retry 4 --retry-delay 5 -o "$tmpDir/$distributionFile" "$distributionUrl" || die "download failed: $distributionUrl"
  fi
elif command -v wget >/dev/null 2>&1; then
  if [ -n "${MVNW_USERNAME-}" ]; then
    wget -q --tries=5 --waitretry=5 --retry-on-http-error=429,500,502,503,504 --user="$MVNW_USERNAME" --password="${MVNW_PASSWORD-}" -O "$tmpDir/$distributionFile" "$distributionUrl" || die "download failed: $distributionUrl"
  else
    wget -q --tries=5 --waitretry=5 --retry-on-http-error=429,500,502,503,504 -O "$tmpDir/$distributionFile" "$distributionUrl" || die "download failed: $distributionUrl"
  fi
else
  die "curl or wget is required to download Maven"
fi

command -v unzip >/dev/null 2>&1 || die "unzip is required to unpack Maven"
unzip -q "$tmpDir/$distributionFile" -d "$tmpDir" || die "failed to unpack $distributionFile"
[ -x "$tmpDir/$distributionName/bin/mvn" ] || die "unexpected archive layout in $distributionFile"

# Move into place; if another process won the race, keep its copy
mv "$tmpDir/$distributionName" "$MAVEN_HOME/" 2>/dev/null || [ -x "$MAVEN_HOME/$distributionName/bin/mvn" ] || die "failed to install Maven into $MAVEN_HOME"
rm -rf "$tmpDir"
trap - EXIT INT TERM

run_maven "$@"
