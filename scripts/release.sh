#!/bin/sh
# Creates a GitHub release for the version in app/build.gradle, then moves the version to the next patch.
#
#   sh scripts/release.sh [--dry-run] [--apk FILE] [--notes TEXT] [--no-bump]
#
# versionName 1.0.3-alpha.12 releases as v1.0.3. Afterwards the version becomes 1.0.4-alpha.1 (versionCode + 1),
# ready for the next round of alphas. Needs the GitHub CLI (gh), logged in with `gh auth login`.
# GRADLE_FILE and REPO can be set in the environment (the defaults are this project's).
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
GRADLE_FILE=${GRADLE_FILE:-$ROOT/app/build.gradle}
REPO=${REPO:-throwarray/tv-settings}
DRY=0; BUMP=1; APK=""; NOTES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --no-bump) BUMP=0 ;;
    --apk) shift; APK=${1:?--apk needs a file} ;;
    --notes) shift; NOTES=${1:?--notes needs text} ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

NAME=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' "$GRADLE_FILE" | head -n 1)
CODE=$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -n 1)
[ -n "$NAME" ] && [ -n "$CODE" ] || { echo "no versionName / versionCode in $GRADLE_FILE" >&2; exit 1; }

VERSION=${NAME%%-*}                       # 1.0.3-alpha.12 -> 1.0.3
case "$VERSION" in *.*.*) ;; *) echo "versionName $NAME is not major.minor.patch" >&2; exit 1 ;; esac
TAG=v$VERSION
MAJOR=${VERSION%%.*}; REST=${VERSION#*.}; MINOR=${REST%%.*}; PATCH=${REST#*.}
NEXT="$MAJOR.$MINOR.$((PATCH + 1))-alpha.1"

# the release builds: the APK to attach, if one was built
if [ -z "$APK" ]; then
  APK=$(ls -t "$ROOT"/app/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1 || true)
fi
[ -z "$NOTES" ] && NOTES="TVSettings $VERSION"

echo "repo:     $REPO"
echo "tag:      $TAG   (from versionName $NAME, versionCode $CODE)"
echo "apk:      ${APK:-none found}"
echo "next:     $NEXT, versionCode $((CODE + 1))"
if [ "$DRY" = 1 ]; then echo "dry run: nothing created, nothing changed"; exit 0; fi

command -v gh >/dev/null 2>&1 || { echo "the GitHub CLI (gh) is needed: pkg install gh, then gh auth login" >&2; exit 1; }
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "release $TAG already exists on $REPO" >&2; exit 1
fi

if [ -n "$APK" ]; then
  gh release create "$TAG" "$APK" --repo "$REPO" --title "TVSettings $VERSION" --notes "$NOTES"
else
  gh release create "$TAG" --repo "$REPO" --title "TVSettings $VERSION" --notes "$NOTES"
fi
echo "released $TAG"

if [ "$BUMP" = 1 ]; then
  sed -i "s/^\([[:space:]]*versionName[[:space:]]*=[[:space:]]*\)\".*\"/\1\"$NEXT\"/; s/^\([[:space:]]*versionCode[[:space:]]*=[[:space:]]*\)[0-9][0-9]*/\1$((CODE + 1))/" "$GRADLE_FILE"
  echo "version is now $NEXT ($((CODE + 1))) in $GRADLE_FILE: commit it"
fi
