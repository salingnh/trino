#!/usr/bin/env python3

"""Validate internal Conventional Commits and upstream Trino subjects."""

import argparse
import re
import subprocess
import sys
import unittest


TYPES = "build|ci|docs|feat|fix|chore|refactor|style|test|perf"
CONVENTIONAL = re.compile(rf"(?:{TYPES}): (\S.*)")
PAST_TENSE = {"added", "changed", "fixed", "improved", "refactored", "removed", "updated"}


def validate_subject(subject):
    errors = []
    if len(subject) > 60:
        errors.append(f"subject: {len(subject)} characters (maximum 60)")
    if not subject or subject != subject.strip() or "\n" in subject or "\r" in subject:
        errors.append("subject: must be a nonempty single line without surrounding whitespace")
    match = CONVENTIONAL.fullmatch(subject)
    description = match.group(1) if match else subject
    if not match and (not subject or not subject[0].isupper()):
        errors.append("subject: use an allowed conventional type or an uppercase upstream subject")
    if description.endswith("."):
        errors.append("subject: must not end with a period")
    if description.split() and description.split()[0].lower() in PAST_TENSE:
        errors.append("subject: use an imperative verb instead of past tense")
    return errors


def check_commits(revision):
    output = subprocess.run(
        ["git", "log", "--no-merges", "--format=%h%x00%s", revision, "--"],
        check=True, capture_output=True, text=True,
    ).stdout
    failed = False
    for line in output.splitlines():
        commit, subject = line.split("\0", 1)
        errors = validate_subject(subject)
        if errors:
            failed = True
            print(f"{commit} {subject}")
            for error in errors:
                print(f"  {error}")
    return int(failed)


class TestSubjects(unittest.TestCase):
    def test_allowed_types(self):
        for kind in TYPES.split("|"):
            with self.subTest(kind=kind):
                self.assertEqual(validate_subject(f"{kind}: improve Elasticsearch checks"), [])

    def test_upstream_subject(self):
        self.assertEqual(validate_subject("Fix Elasticsearch response validation"), [])

    def test_invalid_subjects(self):
        for subject in ["", "fix:", "fix: ", "fix:  improve tests", "other: improve tests",
                        "fix tests", "fix: improve tests.", "fix: added tests", "Added tests",
                        " fix: improve tests", "fix: improve tests ", "fix: improve\ntests",
                        "fix: " + "x" * 56]:
            with self.subTest(subject=subject):
                self.assertTrue(validate_subject(subject))

    def test_length_boundary(self):
        self.assertEqual(validate_subject("fix: " + "x" * 55), [])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_ref", nargs="?")
    parser.add_argument("--test", action="store_true")
    args = parser.parse_args()
    if args.test:
        unittest.main(argv=[sys.argv[0]])
    if not args.base_ref or not re.fullmatch(r"[A-Za-z0-9._/-]+", args.base_ref):
        parser.error("base_ref must be a valid branch name")
    return check_commits(f"origin/{args.base_ref}..HEAD")


if __name__ == "__main__":
    sys.exit(main())
