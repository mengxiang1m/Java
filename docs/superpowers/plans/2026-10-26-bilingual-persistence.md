# Bilingual retrieval and persistent course snapshots

**Goal:** Retrieve English lecture passages for common Chinese Java questions and
restore the project's imported knowledge after IDE restart.

**Architecture:** Expand query terms with a bounded, deterministic course glossary.
Keep BM25, original questions, extracted passages and citation identities intact.
Store versioned document reports and extracted chunks atomically in the IDE's
system cache, separated by project location hash. Rebuild the inverted index from
the saved chunks on the first background operation, without reparsing source files.

**Tech stack:** Existing Java 21 bytecode/JDK 25 toolchain and JUnit; no new dependency.

**Design decisions:** Snapshot semantics: changed or deleted source files do not
silently replace already imported course content. Explicit reimport replaces it;
removal deletes it from both memory and disk. A corrupt or unwritable store is
reported as an error, never silently treated as an empty successful import.
The default standalone constructor remains memory-only. Disk snapshots contain
course text and local paths, never credentials or chat history.

## Implementation and verification

- [x] Add failing cross-language ranking tests, unknown-query and negated-term guards.
- [x] Implement query-only bilingual expansion with longest phrase matching.
- [x] Add persistence restart, removal, replacement, corrupted-store and failed-write tests.
- [x] Implement bounded binary storage and atomic replacement before publishing memory state.
- [x] Wire lazy project restoration off the EDT and project-specific IDE cache paths.
- [x] Run standalone and target-IDE suites; package and structure-check the plugin.
- [x] Measure several Chinese reference questions on the real course corpus;
      rerun live Chinese grounded answers, and archive restart evidence and limits.

## Review focus

Unknown Chinese terms must not expand into generic Java hits. Unchecked-exception
phrases must not also expand into checked exceptions. Disk failures must preserve
the previous searchable snapshot. Restore must preserve blank physical pages and
same-name document identity. Project disposal must not wait for disk I/O on the EDT.
