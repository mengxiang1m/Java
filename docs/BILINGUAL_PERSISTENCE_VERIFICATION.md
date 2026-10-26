# Bilingual retrieval and persistent knowledge verification

Date: 26 October 2026. Branch: `integration/member-d-validation`.

## Implemented behavior

Chinese questions now receive query-only English term expansion from a bounded
Java-course glossary. Covered concepts include checked/unchecked exceptions,
generics, type erasure, JUnit lifecycle, RAG, keyword/vector retrieval, OOP,
annotations and reflection. Longest nonoverlapping phrase matching prevents
`非受检异常` from also expanding as `受检异常`. Original Chinese terms remain
searchable. English-only queries keep the existing BM25 terms and scores; neither
index text nor prompts are translated. No additional model call is made.

The production project service now uses a disk snapshot under the IDE system
directory, `cst3492-knowledge/<project-location-hash>/snapshot.bin`. Reports and
extracted chunks are saved, including canonical paths and physical page/slide
numbers. On the first background operation after reopening a project, the
inverted index is rebuilt from this text without reparsing the original files.
The service constructor does no disk I/O on the EDT.

An explicitly reattached file is reimported on Send, allowing changed course
content to replace the saved version. Failed refreshes keep the previous index
and report an error. Questions without attachments reuse the saved course content.
Backend removal is durable; no new library-management UI was added.

## Storage and lifecycle safeguards

- Binary format has a magic value, explicit version, validated counts/positions,
  and a 64 MiB snapshot limit. It does not use Java object deserialization.
- Writes stage a separate temporary file and atomically replace the snapshot
  before publishing the new in-memory index. Failed writes preserve memory state.
- Invalid/truncated saved data is reported as an error and preserved on disk.
- Project close invalidates detached writers using a nonblocking close flag.
  Saved-state reads and final writes share a disk gate, so a reopened project
  cannot load an older snapshot while a preceding commit is still completing.
- Snapshots contain course content and source paths, not keys or conversation
  history. Storage is outside the source checkout and separated by project.

An independent review identified the initial detached-writer disposal race. It
was repaired and regression-tested; subsequent review found no further actionable
findings. A full native IDE close/reopen session was not driven in this pass.
The final-check/atomic-move interleaving was reviewed; tests cover disposed writers
and cancellation after staging, rather than exhaustively scheduling every race.

## Real course evidence

The runner imported the user's eight PDFs: 195 physical pages, 196 chunks. It
reopened the saved snapshot and compared complete ordered retrieval results,
including scores and source text, for every probe. All were identical. Restored
metadata contained all eight documents and 196 chunks.

| Targeted Chinese question | Top reference page in first five | First result is reference page |
|---|---|---|
| Checked and unchecked exceptions | Yes | Yes |
| Generics and type erasure | Yes | No |
| JUnit before/after operations | Yes | Yes |
| Four RAG steps | Yes | No |
| Combining keyword and vector search | Yes | No |
| Reciprocal rank fusion | Yes | No |
| Annotation uses | Yes | No |
| Inheritance of constructors | Yes | Yes |

Chinese reference-page hit rate: **top five 8/8; top one 3/8**. The original
checked/unchecked Chinese question previously returned zero hits and now ranks
Exceptions page 5 first. The expanded set has 29 labelled questions overall:
**top five 29/29; top one 19/29**. The deliberately unmatched query still has zero
hits. This is a targeted, manually selected glossary evaluation on the actual
course corpus, not an independent or statistically representative multilingual
benchmark. Matching a reference page is not answer accuracy.

The final live run used `DeepSeek-V4.1-Flash` through the actual Java retrieval,
prompt, HTTP, parser and source mapping pipeline, after reopening the snapshot.
Nine live scenarios completed successfully, including four Chinese questions.
Chinese source examples:

- Exceptions: `Lec-3.1-Exceptions.pdf`, page 5.
- Type erasure: `Lec-3.2-Generics-and-Collections.pdf`, pages 11 and 4.
- JUnit: `Lec-3.4-JUnit.pdf`, page 4.
- RAG: `Lec-5-Building-AI-Powered-Java-Applications.pdf`, page 23.

All displayed sources were present in their actual retrieval results; numeric
citations were within the displayed source list. The unmatched question returned
no sources and explicitly stated that it was based on general knowledge. Answers
were inspected for their core course explanation; source validation does not
guarantee that every generated claim or example is factually perfect.

Evidence: [summary](verification/bilingual-persistence-summary.json) and
[complete live/corpus record](verification/bilingual-persistence-live-2026-10-26.json).

## Regression and packaging

| Suite/check | Result |
|---|---|
| Standalone core/RAG | 50 passed, zero failures/errors/skips |
| Standalone integration | 163 passed, zero failures/errors/skips |
| Full target-IDE suite | 230 passed, zero failures/errors/skips |
| Plugin packaging and structure check | Passed |

Suites overlap; their totals must not be added. New tests were first observed
failing for missing bilingual matching, durable reopening, project restoration,
writer disposal, and attachment refresh behavior, then passed after implementation.
Coverage includes deleted source files, PDF/PPT/PPTX page identities, blank pages,
replacement/removal, project isolation, malformed data, failed disk replacement,
cancellation cleanup, background-only restore, and preservation after failed refresh.

## Remaining limits

The glossary cannot interpret arbitrary Chinese paraphrases, multilingual synonyms,
or semantic intent. Top-one ranking remains weaker than top-five coverage. The
cache is a single-IDE local snapshot, not a multi-process shared database or backup;
clearing the IDE system cache can remove it. Atomic replacement is required from
the local file system. Source changes do not automatically replace stored content;
reattach the source to refresh. Large-library heap usage is not separately bounded.
Citation identity checks do not prove every answer claim. Native editor/right-click
and final installed-plugin presentation acceptance remain separate checks.
