# Stepper-NG

A multi-stage repeater extension for Burp Suite with dynamic variable extraction and automated session handling.

Examples & writeup: https://xreous.io/posts/stepper-ng/

> Based on [Stepper](https://github.com/CoreyD97/Stepper) by CoreyD97. Migrated to the Burp Montoya API and extended with new features.

## Usage

### Quick Start
1. **Create a sequence** — Click `+`, double-click the tab to rename it (e.g., `login-seq`).
2. **Add steps** — Click `Add Step` or right-click any request in Burp → *Add to Stepper sequence*. Each step holds one HTTP request and they execute top-to-bottom.
3. **Extract variables** — In a step's post-execution variable table, define a regex to capture values from the response (e.g., `"token":"(.*?)"` with identifier `jwt`). Tip: click **Auto Regex** and highlight text in the response to generate the pattern.
4. **Use variables** — In later steps, insert `$VAR:jwt$` anywhere in the request. It gets replaced with the captured value at execution time.
5. **Run** — Click **Execute** at the bottom to run all steps in order.

### Automatic Session Handling
Stepper-NG can replace Burp's built-in session handling rules + macros. The idea: publish a variable, reference it in your requests, and Stepper-NG keeps it fresh automatically.

1. **Publish a variable** — Check the *Published* checkbox on a post-execution variable (e.g., `jwt` in `login-seq`).
2. **Reference it in other tools** — Use the cross-sequence syntax `$VAR:login-seq:jwt$` in Repeater, Intruder, or Scanner requests. When Burp sends the request, Stepper-NG detects the reference and auto-executes the sequence first.
3. **Validation step** — Select a validation step at the bottom of the sequence panel. Configure its condition (e.g., `If status line matches 200 → Skip remaining steps`). If the session check passes, the rest of the sequence is skipped. If it fails (e.g., 401), the full login/refresh flow runs.
4. **Throttle** — Set *Validate every N requests* in Preferences to avoid firing the sequence on every single request during scans (e.g., validate every 5 requests).

### Variable Syntax

| Type | Syntax | Scope |
|------|--------|-------|
| Step variable | `$VAR:name$` | Within the same sequence |
| Cross-sequence | `$VAR:SequenceName:name$` | From other sequences or Burp tools |
| Static global | `$GVAR:name$` | All tools, set manually |
| Dynamic global | `$DVAR:name$` | All tools, auto-extracted from responses |

## Features

### Published Variables
- **Auto-trigger**: When an outgoing request contains `$VAR:seq:name$` referencing a published variable, the owning sequence auto-executes before the request is sent.
- **Passthrough sync**: Published variables silently capture matching values from any HTTP response flowing through Burp (proxy, repeater, scanner). If a token is refreshed through normal browser activity, the variable updates without re-running the sequence.

### Conditional Steps
Each step can have a condition evaluated after execution:

- **Condition types**: Response body / Status line (with matches/doesn't match) or **Always** (unconditional).
- **Actions** (when condition triggers): Continue to next step, Skip remaining steps, or Go to a named step.
- **Retry**: Re-execute the step N× with a configurable delay. The action fires on the first successful match and remaining retries are skipped.
- **Else**: When the condition doesn't trigger after all retries, an else action fires — enabling if/else branching within a sequence.
- When set to **Always**, retry and else are hidden since the action fires unconditionally.

| Condition | Retry | Then | Else | Use case |
|-----------|-------|------|------|----------|
| If status matches `200` | — | Skip remaining | Continue | Session valid → stop; invalid → keep going |
| If status matches `200` | 2× 500ms | Skip remaining | Continue | Retry refresh; success → done, else → full login |
| If response matches `"error"` | — | Go to step (login) | Continue | Error → jump to login; no error → proceed |
| Always | — | Skip remaining | *(hidden)* | Unconditionally stop after this step |

### Session Validation
Set a **Validation Step** on a sequence (dropdown at the bottom of the sequence panel). The validation step runs first when the sequence is triggered. If its condition fires (e.g., got 200), the session is valid and the remaining steps are skipped. If it doesn't fire (e.g., got 401), the full sequence runs to refresh the session.

### Global Variables
- **Static** (`$GVAR:name$`): Manual key-value pairs in the Global Variables tab. Useful for credentials, hostnames, or any constant.
- **Dynamic** (`$DVAR:name$`): Define a regex + optional host filter. Values auto-update from all HTTP responses flowing through Burp. Inspired by [burp_variables](https://github.com/0xceba/burp_variables).

### Shared Variable Names
When multiple steps extract the same logical value (e.g., a JWT) from different endpoints with different response formats, give them the same identifier. After either step executes, the value syncs to the same-named variable in other steps. Only one needs to be published.

### Other Features
- **Auto-regex generation** — highlight text in a response, click Auto Regex to generate a capture pattern
- **Stepper Replacements tab** — request editor tab previewing all `$VAR:`, `$GVAR:`, `$DVAR:` replacements with highlighting
- **Sequence Overview tab** — summary of all steps showing conditions, variables (✦ = published), current values, and last execution result
- **Sequence arguments** — pass `var=value` pairs via `X-Stepper-Execute-Before: seq: var=val` or `X-Stepper-Argument: var=val` headers
- **Disable/enable** individual steps or entire sequences via right-click (disabled = ⊘ prefix, skipped during execution)
- **Import/export** sequences as JSON files or strings
- **Infinite loop prevention** with max nesting depth

## Changes from Original Stepper

- Migrated from legacy Burp Extender API to Montoya API
- Auto session handler via published variables with validation step and validate-every-N throttling
- Published variables: auto-trigger sequences when referenced, passthrough sync from proxy/tool responses
- Conditional steps with regex/status conditions, match/not-match mode, retry, goto, skip, and else actions
- Session validation mode: run a validation step first, skip sequence if session is alive
- Shared variable names: same-named variables across steps sync values automatically
- Unified Global Variables tab (static `$GVAR:` + dynamic `$DVAR:`) replaces per-sequence globals
- Auto-regex generation from highlighted response text
- Stepper Replacements tab with theme-aware variable highlighting
- Sequence arguments via inline syntax and `X-Stepper-Argument` headers
- Disable/enable individual steps and entire sequences
- Sequence Overview tab showing step conditions, variables, and flow at a glance
- Infinite loop prevention with max depth limit
- Performance optimizations for scanner/proxy workloads
- Fixed Content-Length update for Montoya API compatibility

## Building

```bash
./gradlew clean jar
```

Output: `releases/stepper-ng.jar`

Requirements: Java 25, Gradle 9.4.0 (wrapper included)

## Installation

1. Build or download from [releases](https://github.com/Xre0uS/Stepper-NG/releases)
2. Burp Suite → Extensions → Add → select `stepper-ng.jar`

## Credits

- [CoreyD97](https://github.com/CoreyD97/Stepper) — Original Stepper extension
- [Ratsch0k](https://github.com/C0DEbrained/Stepper/pull/79) — Sequence arguments & disable warning
- [0xceba](https://github.com/0xceba/burp_variables) — Dynamic variables inspiration

## License

See [LICENCE](LICENCE).
