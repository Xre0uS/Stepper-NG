# Stepper-NG

A multi-stage repeater extension for Burp Suite with dynamic variable extraction and automated session handling.

Examples: https://xreous.io/posts/stepper-ng/

> Based on [Stepper](https://github.com/CoreyD97/Stepper) by CoreyD97 which stopped working in Burp Suite 2026 versions. Migrated to the Burp Montoya API and extended with new features.

## Features

### Auto Session Handler
Published variables drive session management automatically. When an outgoing request contains `$VAR:SequenceName:varName$` referencing a published variable, Stepper-NG auto-executes the owning sequence. If the sequence has a Validation Step configured, it runs first - if the session is valid, the rest of the sequence is skipped; if invalid, the full refresh sequence runs.

Use Burp's session handling rules to inject `$VAR:SequenceName:varName$` into headers/cookies/parameters. This replaces the need for manual `X-Stepper-Execute-Before` headers and the old "Check session is valid" + macro approach.

Configure "Validate every N requests" in Preferences to throttle how often published variable sequences fire (e.g., every 5 requests for intruder/scanner). Set to 1 for every request.

### Published Variables

Mark any post-execution variable as Published using the checkbox in the variable table or via the Variables panel in the Overview tab.

How it works:

1. Auto-trigger: When an outgoing request contains `$VAR:SequenceName:varName$` referencing a published variable, the sequence automatically executes before the request - no `X-Stepper-Execute-Before` header needed.

2. Passthrough sync: Published regex variables automatically update when their pattern matches in any HTTP response flowing through Burp (proxy, repeater, scanner, etc.). If a user logs in through the browser and the response contains a token that matches a published variable's regex, the variable silently captures it. This means Stepper stays in sync with tokens refreshed through normal browsing without re-running the sequence.

3. Sequence execution: When a published variable's value is empty or stale (combined with a validation step), referencing it in a request triggers the full sequence to run and capture fresh values.

### Conditional Steps & Session Validation
Each step can have a condition evaluated after execution.

The condition is configured as a single line: If [response body / status line] [matches / does not match] [pattern], optional retry N× with delay, then [continue / skip remaining / go to step].

Session Validation Mode: Set a "Validation Step" on a sequence. Configure its condition to describe what a valid session looks like, e.g. `If status line matches 200`:
- If the condition triggers (e.g., got 200) → session is valid → rest of the sequence is skipped
- If the condition does not trigger (e.g., got 401) → session is invalid → full sequence runs

This mirrors Burp's built-in session handling rules and solves the JWT refresh problem where the first step needs dynamic values from a prior session.

### Shared Variable Names Across Steps

When multiple steps in a sequence extract the same logical value (e.g., a JWT) but from different endpoints with different response formats, give them the same variable identifier. Stepper-NG automatically syncs values across same-named variables:

- Step 1 (login): variable `jwt` with regex `"token":"(.*?)"` → captures from `{"token":"abc"}`
- Step 2 (refresh): variable `jwt` with regex `"jwtToken":"(.*?)"` → captures from `{"jwtToken":"xyz"}`

After either step executes, the captured value propagates to the same-named variable in the other step. `$VAR:seq:jwt$` always resolves to the latest value. Only one of the two `jwt` variables needs to be published.

This also works with passthrough sync - if a login response flows through the proxy and matches Step 1's pattern, the value is synced to Step 2's `jwt` variable automatically.

### Step Sequences
- Define multi-step HTTP request sequences
- Extract variables from responses using regex
- Use variables in subsequent requests with `$VAR:name$` syntax
- Execute sequences manually or triggered via headers (`X-Stepper-Execute-Before`, `X-Stepper-Execute-After`)
- Import and export sequences as JSON files or strings

### Global Variables
A unified Global Variables tab manages two kinds of variables available across all tools:

Static Global Variables
- Simple key-value pairs set manually
- Use in any request with `$GVAR:name$` syntax

Dynamic Global Variables
Inspired by [burp_variables](https://github.com/0xceba/burp_variables), Stepper-NG can automatically extract values from all HTTP responses flowing through Burp Suite.
- Define regex patterns that run against every response
- Optionally filter by host regex
- Use extracted values in any request with `$DVAR:name$` syntax
- Variables update automatically as new matching responses arrive

### Auto-Generate Regex from Selection
When creating post-execution variables, highlight text in a response and Stepper-NG will automatically generate a regex pattern using surrounding context as anchors.

### Sequence Overview Tab
Each sequence has an Overview tab with two panels:
- Steps table: Summary of all steps showing status, target, condition, variables (✦ marks published), actions, and last execution result.
- Variables panel: Lists all post-execution variables across all steps with a checkbox to publish/unpublish. Shows the variable's regex, current value, and last updated timestamp. Right-click to copy the `$VAR:` reference.

### Stepper Replacements Tab
A custom request editor tab shows a preview of the request with all `$VAR:`, `$GVAR:`, and `$DVAR:` variables replaced and highlighted. Respects the current Burp theme (dark/light).

### Sequence Arguments
Pass arguments to sequences when executing them from other tools. Arguments override global variable values for the duration of that execution.

Inline syntax:
```
X-Stepper-Execute-Before: MySequence: token=abc123; user=admin
```

Dedicated argument header (one per header, supports any character in the value):
```
X-Stepper-Argument: token=abc123
```

Arguments from both methods are merged. Inline arguments take priority over `X-Stepper-Argument` headers.

### Disable/Enable Steps & Sequences
Right-click any step or sequence tab to toggle it between enabled and disabled. Disabled steps are skipped during execution. Disabled sequences have their variables left as literal text, will not auto-execute, and ignore execution headers. Both show a ⊘ prefix in the tab title and the state is persisted across project saves.

## Variable Syntax

| Type | Syntax | Scope |
|------|--------|-------|
| Step Variable | `$VAR:name$` | Within a sequence |
| Cross-Sequence Variable | `$VAR:SequenceName:name$` | Across sequences (in other tools) |
| Static Global Variable | `$GVAR:name$` | All tools, set manually |
| Dynamic Global Variable | `$DVAR:name$` | All tools, auto-extracted from responses |

Published variables should typically live in one sequence only. If multiple sequences have published variables, a request containing `$VAR:` references could trigger multiple sequences. The Overview tab shows a warning when conflicts exist.

The `X-Stepper-Execute-Before` header still works for backward compatibility and for cases where you want to trigger a sequence without referencing specific variables.

## Changes from Original Stepper

- Migrated from legacy Burp Extender API to Montoya API
- Auto session handler via published variables with validation step and validate-every-N throttling
- Published variables: auto-trigger sequences when referenced, passthrough sync from proxy/tool responses
- Conditional steps with regex/status conditions, match/not-match mode, retry, goto, and skip actions
- Session validation mode: run a validation step first, skip sequence if session is alive
- Shared variable names: same-named variables across steps sync values automatically
- Unified Global Variables tab (static `$GVAR:` + dynamic `$DVAR:`) replaces per-sequence globals
- Auto-regex generation from highlighted response text
- Stepper Replacements tab with theme-aware variable highlighting
- Sequence arguments via inline syntax and `X-Stepper-Argument` headers ([PR #79](https://github.com/C0DEbrained/Stepper/pull/79))
- Disable/enable individual steps and entire sequences
- Option to disable non-UTF-8 character warning dialog ([PR #78](https://github.com/C0DEbrained/Stepper/pull/78))
- Tab indicators when sequences fire in the background
- Infinite loop prevention with max depth limit
- Duplicate sequence support (right-click tab)
- Sequence Overview tab showing step conditions, variables, and flow at a glance
- Fixed Content-Length update for Montoya API compatibility
- Performance optimizations and bug

## Building

```bash
./gradlew clean jar
```

The built jar will be at `releases/stepper-ng.jar`.

Requirements:
- Java 25 LTS
- Gradle 9.4.0 (included via wrapper)

## Installation

1. Build the jar or download from releases
2. In Burp Suite, go to Extensions → Add
3. Select the `stepper-ng.jar` file
4. The Stepper-NG tab will appear

## Credits

- CoreyD97 - Original [Stepper](https://github.com/CoreyD97/Stepper) extension
- Ratsch0k - Sequence arguments ([PR #79](https://github.com/C0DEbrained/Stepper/pull/79)) and disable unprocessable warning ([PR #78](https://github.com/C0DEbrained/Stepper/pull/78))
- Inspired by [burp_variables](https://github.com/0xceba/burp_variables) by 0xceba

## License

See [LICENCE](LICENCE) file.

