# Contributing to FlameForge

## Development Requirements

- Read the implemented source and nearby contract tests before changing a
  compatibility path.
- Keep changes narrow. Do not add a dependency or claim a platform API that is
  not present in the project source or build configuration.

## Build and Verification

Use the project build command after a coherent change:

```bash
mvn clean install
```

Documentation-only changes do not require a build from this contribution
guide, but implementation changes must leave the Maven build passing.

## Scope / Worktree Discipline

- Preserve unrelated worktree changes and documentation hunks.
- Touch only files required by the change. Do not rewrite catalogs or unrelated
  compatibility bridges as cleanup.
- Do not use version branches for particle behavior.

## Java 8 Compatibility

- Source and bytecode compatibility is Java 8. Avoid APIs, syntax, and library
  assumptions newer than Java 8 unless the existing project already requires
  them.
- Keep reflection and optional symbols isolated so loading an older server does
  not link modern-only classes.

## Bukkit / Spigot / Paper / Folia Compatibility

- The supported dynamic range is Spigot/Bukkit/Paper/Folia from 1.8.8 through
  modern 1.21.x, subject to each runtime's available capabilities.
- Use the exact runtime lookup first: inspect the running enum, key, method, or
  capability before selecting a behavior. Do not introduce version branches.
- Preserve entity, region, and scheduler ownership. Do not claim that every
  Bukkit call runs on a global main thread.
- Modern-only APIs must remain reflective or otherwise isolated from the 1.8.8
  load path.
- Add or update a compatibility profile regression when a runtime contract
  changes.

## Particle Engine Rules

- `ParticleBridge` owns request delivery and provider fallback. Keep cosmetic
  failures isolated from gameplay.
- `ParticleProviderFactory` keeps the ordered chain: reflective Bukkit
  provider, legacy `Effect` provider, then fallback handling.
- `getDataType()` wins over version assumptions. The runtime descriptor decides
  whether a particle uses `NONE`, a known typed payload, or custom typed data.
- Unknown typed particles use `CustomPayload` only when the value matches the
  runtime-required type; otherwise continue the ordered candidate/provider
  fallback.
- Do not guess constructors. Resolve the exact runtime constructor and skip
  the typed candidate when it is unavailable.
- Keep styles semantic: RGB and ordered candidates belong in the style catalog,
  not in individual effect call sites.
- Keep pattern and network geometry pure. Geometry builders calculate points;
  `ParticleBridge` and the owning scheduler perform spawning.
- Batch related requests where possible and preserve the 2048-request bounds.
- `Player` spawn is entity-owned. Schedule sends for the viewing player, and
  separately for another player who owns a displayed copy.

## Adding a Particle Alias

- Add the alias and its semantic family to `ParticleCatalog`.
- Preserve raw candidate order first, then alias and family candidates without
  duplicates.
- Do not rename a runtime enum in a call site. A catalog rename is a catalog
  alias/family change with a compatibility regression.

## Adding a Particle Style

- Add the semantic ID and its RGB palette and ordered candidates to
  `ParticleStyleCatalog`.
- Use stable semantic names such as outcome or power meaning, not a single
  server version's enum name.
- Update the style catalog regression and any result-theme mapping that uses
  the style.

## Adding Typed Particle Data

- Start with runtime `getDataType()` and the exact spawn method signature.
- Add a `ParticleRequest.Payload` type only when the source has a real payload
  contract. Add exact reflective construction for the matching runtime type.
- Unknown types use `CustomPayload`; incompatible values must fall through to
  the next candidate/provider. Never infer a constructor from a type name.
- Cover both a no-data profile and the relevant typed profile, including the
  1.21.8 `NONE` versus 1.21.9 `Spell` distinction when applicable.

## Adding a Pattern

- Put bounded coordinate math in `ParticlePatternBuilder` or a pattern helper.
- Keep it pure: no `Player`, scheduler, world mutation, or particle spawning in
  geometry code.
- Enforce finite inputs and the existing 2048-point limit.
- Use `ParticleNetworkRenderer` for ordered parent-to-child frames and let the
  owning player scheduler send the resulting batches.

## Scheduler / Thread Ownership

- Player-owned particle sends use the player entity scheduler through
  `ParticleBridge`.
- Folia region or chunk work uses the appropriate region-aware bridge.
- Do not move I/O into entity callbacks or move world/entity mutations onto an
  arbitrary async executor.
- Treat cosmetic scheduling failure as isolated; do not swallow gameplay
  failures as particle success.

## Tests

- Add a focused compatibility profile regression for every changed runtime
  contract.
- Test candidate ordering, typed payload acceptance/rejection, and fallback
  behavior rather than only the happy-path enum.
- Test pure pattern bounds and network frame ownership without starting a
  server.

## Documentation

- Document actual source behavior, including fallback limits and ownership.
- Update README, architecture, or outcome documentation when a public
  compatibility rule changes.
- Do not document unsupported server versions, dependencies, or APIs.

## Pull Request Checklist

- [ ] No version branches were added.
- [ ] Exact runtime lookup happens first.
- [ ] Alias/catalog changes preserve candidate order and have regression cover.
- [ ] `getDataType()` determines typed adaptation.
- [ ] Modern-only symbols remain reflective or isolated.
- [ ] Unknown typed data uses `CustomPayload` and fallback correctly.
- [ ] No constructor guessing was added.
- [ ] Pattern math remains pure.
- [ ] Player spawning remains entity-owned.
- [ ] Compatibility profile regression is present for changed behavior.
- [ ] `mvn clean install` passes for implementation changes.
- [ ] Worktree scope contains no unrelated edits.
