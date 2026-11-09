#!/usr/bin/env python3

"""Recognize the reviewed upstream merge without relaxing other PR checks."""

import argparse
import re
import subprocess
import sys
import unittest


# Keep the upstream SHAs intact for this production integration. Remove this
# exception once the merge is present on internal/production.
REVIEWED_SYNC_MERGE = "9a7de5c6fc601386d89ed4f7b7b6361c86ab3c3f"


def is_reviewed_sync(base_ref, merge_commits):
    return base_ref == "internal/production" and merge_commits == [REVIEWED_SYNC_MERGE]


class TestReviewedSync(unittest.TestCase):
    def test_reviewed_production_merge(self):
        self.assertTrue(is_reviewed_sync("internal/production", [REVIEWED_SYNC_MERGE]))

    def test_other_pull_requests_keep_merge_checks(self):
        for base, merges in [
            ("master", [REVIEWED_SYNC_MERGE]),
            ("internal/production", []),
            ("internal/production", ["a" * 40]),
            ("internal/production", [REVIEWED_SYNC_MERGE, "a" * 40]),
            ("internal/production", [REVIEWED_SYNC_MERGE[:7]]),
        ]:
            with self.subTest(base=base, merges=merges):
                self.assertFalse(is_reviewed_sync(base, merges))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_ref", nargs="?")
    parser.add_argument("head_sha", nargs="?")
    parser.add_argument("--test", action="store_true")
    args = parser.parse_args()
    if args.test:
        unittest.main(argv=[sys.argv[0]])
    if not args.base_ref or not re.fullmatch(r"[A-Za-z0-9._/-]+", args.base_ref):
        parser.error("base_ref must be a valid branch name")
    if not args.head_sha or not re.fullmatch(r"[0-9a-f]{40}", args.head_sha):
        parser.error("head_sha must be a full commit SHA")
    revision = f"refs/remotes/origin/{args.base_ref}..{args.head_sha}"
    merges = subprocess.run(
        ["git", "rev-list", "--merges", revision],
        check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    print(f"reviewed={str(is_reviewed_sync(args.base_ref, merges)).lower()}")


if __name__ == "__main__":
    main()
