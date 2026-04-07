# GitBucket MCP Plugin

A GitBucket plugin that fixes API deficiencies and autoclose limitations.

**Written in Scala** (extends GitBucket's Scala plugin system - Java cannot be used for GitBucket plugins).

## Build & Install (Simple)

```bash
# Clone and build
git clone https://github.com/NewsRx/gitbucket-mcp-plugin.git
cd gitbucket-mcp-plugin
sbt assembly

# Copy uber JAR to GitBucket plugins (creates JAR with all dependencies)
cp target/scala-2.13/gitbucket-mcp-plugin-assembly-0.1.0.jar $GITBUCKET_HOME/plugins/

# Restart GitBucket
```

The `sbt assembly` command creates an **uber JAR** with all dependencies bundled - just drop it in the plugins folder.

## Why This Plugin Is Needed

### API Deficiencies

| Endpoint | GitBucket | GitHub | Impact |
|----------|-----------|--------|--------|
| `PATCH /repos/:owner/:repo/issues/:number` | ❌ Missing | ✅ Present | Cannot update issue state via API |
| `PUT /repos/:owner/:repo/issues/:number/labels` | ❌ Missing | ✅ Present | Cannot manage labels via API |
| `POST /repos/:owner/:repo/issues/:number/assignees` | ❌ Missing | ✅ Present | Cannot manage assignees via API |

### Autoclose Limitation

GitBucket only autocloses issues when merging to the **default branch**, not arbitrary branches like `dev` or `feature/*`:

```scala
// MergeService.scala line 370:
if (pullRequest.branch == defaultBranch) {
  closeIssuesFromMessage(issueContent, ...)
}
```

**Impact**: PRs to `dev` branch with "Fixes #123" in description don't autoclose the issue.

## Plugin Architecture

The plugin:

1. **Registers hooks**: Intercepts PR merge events
2. **Bypasses branch restriction**: Closes issues from any branch merge
3. **Provides API endpoints**: Adds missing GitHub-compatible endpoints at `/api/mcp/v1/...`
4. **Uses standard HTTPS**: Same server, same authentication as GitBucket

All endpoints use GitBucket's existing auth and URL pattern:

```
https://your-gitbucket-server/api/mcp/v1/repos/:owner/:repo/issues/:number
```

## Impact on Three-Branch Workflow

The three-branch workflow (`feature/*` → `dev` → `main`) is broken by GitBucket's deficiencies:

### Issue State Cannot Be Updated

**Workflow**: AI agent creates spec #123, implements on `feature/spec-123`, merges to `dev`

**Problem**: No `PATCH /issues/:number` endpoint means agent cannot:
- Close issue after implementation
- Update issue state through workflow phases
- Signal "ready for review" to humans

**Result**: Workflow HALTS waiting for manual UI clicks

### Labels Cannot Be Managed

**Workflow**: Spec moves through phases (`needs-approval` → `approved` → `in-progress` → `review`)

**Problem**: No API to add/remove labels

**Result**: Manual label management breaks automation

### Autoclose Only on Default Branch

**Workflow**: PR merged from `feature/spec-123` to `dev`

**Expected**: Issue autocloses with "Fixes #123" in PR description

**Actual**: Issue NOT closed (only `main` is default branch)

**Result**: Requires manual close for every PR

## API Endpoints

### PATCH /api/mcp/v1/repos/:owner/:repo/issues/:number

Update issue title, body, or state.

```json
{ "state": "closed" }
```

### PUT /api/mcp/v1/repos/:owner/:repo/issues/:number/labels

Replace labels.

```json
{ "labels": ["approved", "in-progress"] }
```

### POST /api/mcp/v1/repos/:owner/:repo/issues/:number/assignees

Add assignees.

```json
{ "assignees": ["reviewer1", "reviewer2"] }
```

## Development

```bash
# Compile
sbt compile

# Run tests  
sbt test

# Build uber JAR
sbt assembly

# Output: target/scala-2.13/gitbucket-mcp-plugin-assembly-0.1.0.jar
```

## Project Structure

```
src/main/scala/com/newsrx/mcpplugin/
├── MCPPlugin.scala              # Plugin entry point
├── AutocloseHook.scala          # Hook: PR merge → close issues
└── api/
    ├── IssueUpdateEndpoint.scala # PATCH /issues/:number
    ├── LabelEndpoint.scala       # PUT /issues/:number/labels
    └── AssigneeEndpoint.scala    # POST /issues/:number/assignees
```

## License

Apache License 2.0 (compatible with GitBucket)