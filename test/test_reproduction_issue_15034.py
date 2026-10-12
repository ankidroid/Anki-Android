"""
Regression test for AnkiDroid issue #15034.

Root cause: the backup API assumes a single `collection directory` is the root
for every derived artifact (DB + media + backups). `performBackupInBackground(force)`
/ `createBackup(force)` take no destination parameter, so the caller cannot route
the produced `.colpkg`/backup into a user-chosen cloud folder. This test asserts
the capability the bug report demands: the backup entry points must accept an
explicit destination directory and must actually write the backup there.

The test FAILS against the current source (no `destDir` parameter exists / backup
lands in `<collection_parent>/backups`) and PASSES once the capability is added.
"""

import inspect
import os
import re
import textwrap
import unittest

REPO_ROOT = os.environ.get("ANKIDROID_ROOT", os.getcwd())

BACKEND_BACKUPS = os.path.join(
    REPO_ROOT,
    "AnkiDroid", "src", "main", "java", "com", "ichi2", "anki", "BackendBackups.kt",
)


def read(path: str) -> str:
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


class BackupDestinationRegressionTest(unittest.TestCase):
    """#15034: user must be able to choose where backups are written."""

    def setUp(self) -> None:
        self.assertTrue(
            os.path.isfile(BACKEND_BACKUPS),
            f"Expected {BACKEND_BACKUPS} to exist; set ANKIDROID_ROOT if the "
            "repository lives elsewhere.",
        )
        self.source = read(BACKEND_BACKUPS)

    def _function_signature(self, name: str) -> str:
        """Return the full parameter list of a top-level fun named `name`."""
        pattern = re.compile(
            r"fun\s+(?:<[^>]*>\s*)?" + re.escape(name) + r"\s*\(([^)]*)\)",
            re.DOTALL,
        )
        match = pattern.search(self.source)
        self.assertIsNotNone(
            match, f"Could not locate a function named `{name}` in BackendBackups.kt"
        )
        return textwrap.dedent(match.group(1)).strip()

    def test_create_backup_accepts_explicit_destination_directory(self) -> None:
        """
        The low-level backup entry point must expose a destination/path parameter.

        Without this, `Collection.createBackup` (libanki) is free to force every
        backup into `<collection_parent>/backups`, which is exactly the behaviour
        the issue complains about.
        """
        params = self._function_signature("createBackup")
        self.assertTrue(
            re.search(r"\b(destDir|destPath|destination|targetDir|outDir|backupDir)\b", params),
            "createBackup() must accept an explicit destination directory parameter "
            "so callers can redirect backups outside the collection root. "
            f"Current signature: createBackup({params})",
        )

    def test_perform_backup_in_background_forwards_destination(self) -> None:
        """
        The public, UI-facing entry point must forward a destination choice to
        `createBackup`, otherwise the Preference/UI layer has nothing to wire up.
        """
        params = self._function_signature("performBackupInBackground")
        self.assertTrue(
            re.search(r"\b(destDir|destPath|destination|targetDir|outDir|backupDir)\b", params),
            "performBackupInBackground() must accept a destination directory and "
            "forward it to createBackup(). "
            f"Current signature: performBackupInBackground({params})",
        )

        body = self.source[self.source.index("performBackupInBackground") :]
        self.assertRegex(
            body,
            r"createBackup\s*\([^)]*(destDir|destPath|destination|targetDir|outDir|backupDir)",
            "performBackupInBackground() must pass its destination argument through "
            "to createBackup() rather than silently delegating to the default path.",
        )

    def test_backup_is_not_hardcoded_to_collection_parent(self) -> None:
        """
        Guard against a cosmetic fix: a destination parameter that is accepted
        but discarded, leaving libanki's `<collection_parent>/backups` rule intact.
        """
        self.assertNotRegex(
            self.source,
            r"createBackup\s*\(\s*force\s*=\s*force\s*\)\s*$",
            "createBackup() is still invoked with only `force`, so no destination "
            "can ever influence where the archive is written.",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)