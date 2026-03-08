# Global Rules (ygflow-suite)

## Encoding (Mandatory)

1. All new or modified files must be saved as `UTF-8` (no BOM).
2. Garbled text is strictly forbidden (for example: mojibake, broken CJK strings, truncated literals).
3. Do not use `GBK`, `ANSI`, or `UTF-16` for source code, configs, or docs.
4. UI copy, logs, and error messages must remain human-readable after save/build.
5. If a touched file contains historical mojibake, fix it in the same change.
6. If any newly discovered file is not UTF-8, convert it to UTF-8 (no BOM) before further edits.

## Pre-commit Checks (Mandatory)

1. Run build/tests for affected modules and ensure they pass.
2. Verify changed files do not contain mojibake or malformed string literals.
3. Ensure IDE/editor project encoding is set to `UTF-8`.
