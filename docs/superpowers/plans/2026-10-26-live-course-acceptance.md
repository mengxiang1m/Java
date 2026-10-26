# Real-course and Tongji acceptance plan

**Goal:** Configure the user's Tongji endpoint/model locally and test the actual uploaded course corpus using the existing production pipeline.

**Constraints:** Never commit an API key. The key is protected with Windows DPAPI outside the checkout. Public settings and tool paths also remain in that local profile. Keep the existing plugin architecture. Do not confuse a network refusal, lexical retrieval result, or fixture response with a real model answer.

- [x] Find the eight uploaded lecture PDFs and inspect reference pages.
- [x] Store the supplied key under current-user Windows encryption and save public endpoint/model settings.
- [x] Probe the real endpoint: HTTP 403, current network environment unsupported.
- [x] Add a reusable real-course retrieval/live acceptance runner and local-profile launcher.
- [x] Run local retrieval over all eight PDFs: 195 pages, 196 chunks; reference page top1=16/22 and top5=21/22.
- [x] Run the live Java pipeline and record the blocking conditions: direct connection timeout; Windows proxy tunnel HTTP 502. PowerShell separately reports HTTP 403/network unsupported. Live answers remain incomplete.
- [x] Run deterministic regression tests (149 standalone, 215 target IDE), independent review and document commands/results.

The user has been asked to switch to an allowed campus/VPN network. Independent local verification continues while that answer is pending.
