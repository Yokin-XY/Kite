# Kite agent rules

This project uses Ponytail-style guidance for coding-agent work.
Source: https://github.com/DietrichGebert/ponytail
Adapted from Ponytail, MIT License, copyright (c) 2026 DietrichGebert.

## Ponytail, Lazy Senior Dev Mode

Be efficient, not careless. Before writing code, stop at the first rung that
holds:

1. Does this need to be built at all? If not, skip it.
2. Does the standard library already do this? Use it.
3. Does a native platform feature cover it? Use it.
4. Does an already-installed dependency solve it? Use it.
5. Can this be one line? Make it one line.
6. Only then, write the minimum code that works.

Rules:

- No abstractions that were not explicitly requested.
- No new dependency if the existing platform or dependency set can cover it.
- No boilerplate nobody asked for.
- Prefer deletion over addition, boring over clever, and the fewest files possible.
- Question complex requests when a simpler path may cover the real need.
- Pick the edge-case-correct option when two standard-library approaches are the same size.
- Mark intentional shortcuts with a `ponytail:` comment.

If a shortcut has a known ceiling, the `ponytail:` comment must name the ceiling
and the upgrade path.

Never be lazy about trust-boundary validation, data-loss prevention, security,
accessibility, real-device calibration, or anything explicitly requested.

Non-trivial logic needs one runnable check: the smallest test, assertion, or
self-check that fails if the logic breaks. Trivial one-liners do not need a test.

## Kite Real-Device Review Habit

For Kite, user-visible changes should be made inspectable on the real phone
when practical. This applies especially to visible UI behavior, runtime flow,
cards, resources, terminal/report/Web surfaces, install flows, and interaction
feedback.

Do not treat phone deployment as a rigid requirement for every tiny edit. The
goal is to get the changed effect in front of the user for human review when it
materially improves confidence.

Default device, ADB, build, and install details belong in
`references/toolchain.md`. This file defines the review habit, not the device
contract.
