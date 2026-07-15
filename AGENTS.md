\# Totality Codex Instructions



\## Project



\- Totality is a private Fabric Minecraft mod.

\- Use the versions and mappings currently declared in the project build files.

\- Read relevant canonical documents under `Context/Audit/` before planning changes.

\- Newer design and audit decisions override older documents.

\- Do not silently redesign established mechanics.



\## External reference code



\- `Inspiration Mods/` contains external mod source used only as reference material.

\- It is not part of Totality.

\- Never modify it.

\- Never count it as Totality implementation.

\- Exclude it from audits and repository-wide implementation inventories unless explicitly asked to compare against it.



\## Safety



\- Keep changes scoped to the requested task.

\- Do not modify source or resources during documentation-only audits.

\- For audit tasks, write only under `Context/Audit/`.

\- Run `git status --short` before finishing and report every changed file.

\- Never claim a feature is implemented only because a class, JSON file, comment, TODO, or design document exists.

\- Confirm registration and reachable production call paths.



\## Verification



\- Run appropriate Gradle builds or verification classes when code is changed.

\- Report changed files, commands run, tests, unresolved issues, and deviations.

