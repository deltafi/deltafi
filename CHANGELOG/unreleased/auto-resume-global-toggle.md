# Changes on branch `auto-resume-global-toggle`

### Added
- New system property `autoResumeEnabled` (group: error controls, default: `true`). When set to `false`:
  - The scheduled auto-resume task skips all work regardless of individual rule state.
  - New errors are no longer matched against resume policies, so newly errored DeltaFiles are not stamped with a `nextAutoResume` (matches the behavior of disabling every individual policy).
  - DeltaFiles already carrying a `nextAutoResume` timestamp keep it; they are simply not acted on by the scheduler while the global toggle is off. When the toggle flips back to `true`, the scheduler picks them up on its next pass.
