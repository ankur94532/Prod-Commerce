"""Is this container image built from the source tree as it stands?

ops/testing/data-tier-deploy.sh starts real service images against the data tier it just
stood up. An image older than the code it claims to run makes every assertion after it
meaningless, and quietly so: a stale image starts, connects to PostgreSQL and behaves like a
service, just not like this one. The first run of that drill spent ten minutes concluding
that migrations hang, when what was actually installed was a seven-month-old build in which
Flyway had not been wired up yet.

Prints the first source file newer than the image, and exits 1, when the image is stale.
`find -newermt @<epoch>` would do this in one line on GNU find and is not available on the
BSD find macOS ships, which is why the comparison lives here.

Usage: image-is-current.py <image-created-iso8601> <source-dir>...
"""
from __future__ import annotations

import datetime as dt
import pathlib
import re
import sys


def image_epoch(created: str) -> float:
    """Seconds since the epoch for Docker's RFC3339 `.Created`, e.g.
    2026-09-09T14:52:25.560398095Z.

    Parsed with an explicit pattern rather than by slicing around the '.', because the
    fractional part and the timezone offset both contain digits: an earlier version counted
    the digits in "560398095+00:00" as one run, lost the offset, and read a UTC timestamp as
    local time. On a +05:30 machine that shifted every image five and a half hours into the
    past and reported freshly built images as stale.
    """
    if not created.strip():
        # Usually "docker image inspect" found nothing, or its output was mangled by the
        # caller's shell. Either way the answer is "cannot tell", which must not look like
        # a stack trace in the middle of a drill.
        raise ValueError('no image timestamp given; is the image built, and did the shell '
                         'pass {{.Created}} through unexpanded?')
    match = re.match(
        r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$',
        created.strip())
    if match is None:
        raise ValueError(f'unrecognised image timestamp: {created!r}')
    stamp, fraction, offset = match.groups()
    # datetime accepts at most microseconds; Docker reports nanoseconds.
    stamp += '.' + (fraction or '0')[:6].ljust(6, '0')
    offset = (offset or 'Z').replace('Z', '+00:00')
    if len(offset) == 5:  # +0530 -> +05:30
        offset = offset[:3] + ':' + offset[3:]
    return dt.datetime.fromisoformat(stamp + offset).timestamp()


# Build output, and macOS metadata that Finder rewrites whenever a directory is browsed.
# Neither changes what the service does, and treating .DS_Store as source makes every image
# on a Mac permanently "stale" — a check that always fails gets switched off, which is worse
# than not having it.
IGNORED_DIRECTORIES = {'target', 'node_modules', '.git'}
IGNORED_FILES = {'.DS_Store'}


def newer_than(epoch: float, roots: list[str]) -> pathlib.Path | None:
    for root in roots:
        path = pathlib.Path(root)
        candidates = path.rglob('*') if path.is_dir() else [path]
        for candidate in candidates:
            if not candidate.is_file():
                continue
            if IGNORED_DIRECTORIES.intersection(candidate.parts):
                continue
            if candidate.name in IGNORED_FILES:
                continue
            if candidate.stat().st_mtime > epoch:
                return candidate
    return None


def main(argv: list[str]) -> int:
    try:
        epoch = image_epoch(argv[1])
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2
    stale = newer_than(epoch, argv[2:])
    if stale is not None:
        print(stale)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
