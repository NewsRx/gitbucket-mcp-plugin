# GitBucket MCP Plugin

A GitBucket plugin that fixes API deficiencies and autoclose limitations via MCP (Model Context Protocol) integration.

**Must be written in Scala** (not Java) because GitBucket plugins:
- Extend Scala traits (`Plugin`, `PullRequestHook`, `Controller`)
- Use Scalatra framework for routing
- Access GitBucket's internal services (all Scala)
- Register via Scala reflection system

Java code cannot directly implement GitBucket plugin hooks.

## Why This Plugin Is Needed

### Autoclose Deficiency in GitBucket

GitBucket's native autoclose functionality has limitations that impact automated workflows:

1. **Branch Restriction Bug**: Autoclose only works when merging to the repository's **default branch**, not arbitrary branches:
   ```scala
   // From MergeService.scala:
   if (pullRequest.branch == defaultBranch) {
     closeIssuesFromMessage(issueContent, ...)
   }
   ```

2. **Three Locations With Default Branch Guard**:
   - `MergeService.scala` - PR merge autoclose
   - `RepositoryCommitFileService.scala` - Web UI file edit autoclose  
   - `GitRepositoryServlet.scala` - Git push autoclose

3. **Impact**: Issues referenced in PR descriptions with `fixes #N`, `closes #N`, etc. are not autoclosed when merging to non-default branches like `dev` or `feature/*` branches.

4. **Documentation Inaccuracy**: The GitBucket wiki states PR-level autoclose is "not yet available in 3.4" but the source code shows it IS implemented - just restricted to default branch.

### API Deficiencies

GitBucket implements GitHub-compatible API v3 but with gaps:

| Endpoint | GitBucket | GitHub | Impact |
|----------|-----------|--------|--------|
| `PATCH /repos/:owner/:repo/issues/:number` | ❌ Missing | ✅ Present | Cannot update issue title, body, state via API |
| `PUT /repos/:owner/:repo/issues/:number/labels` | ❌ Missing | ✅ Present | Cannot manage labels via API |
| `POST /repos/:owner/:repo/issues/:number/assignees` | ❌ Missing | ✅ Present | Cannot manage assignees via API |

These gaps prevent MCP tools from managing GitBucket issues the same way they manage GitHub issues.

## Impact on Three-Branch Workflow

The three-branch workflow (`feature/*` → `dev` → `main/master`) requires robust issue tracking across all branches. GitBucket's deficiencies severely impact AI agents managing specs in this workflow:

### Workflow Overview

```
feature/spec-123  →  dev  →  main/master
     │                │           │
     │                │           └─ Production release
     │                └─ Integration/testing
     └─ Feature development
```

### How API Gaps Break AI Agent Workflows

#### 1. **Issue State Cannot Be Updated via API**

**Workflow Stage**: Feature branch → Dev merge

**Problem**: AI agent creates spec issue #123, implements feature on branch `feature/spec-123`, and needs to update the issue state when merging to `dev`.

**GitHub API**:
```bash
PATCH /repos/:owner/:repo/issues/123
{
  "state": "closed"
}
```

**GitBucket API**: ❌ Missing - No PATCH endpoint for issues

**Workaround Required**: 
- Manual UI clicks to close issue
- Or database direct manipulation
- Or custom plugin endpoint

**Impact on AI Agent**:
- Cannot programmatically close spec issues
- Workflow HALTs waiting for human intervention
- Breaks sub-issue tracking workflow
- Prevents automated "PR merged, closing issue #N" updates

#### 2. **Labels Cannot Be Managed via API**

**Workflow Stage**: Issue triage, spec approval, implementation phases

**Problem**: AI agent needs to add/remove labels for workflow states:
- `needs-approval` → awaiting human approval
- `approved` → ready for implementation
- `in-progress` → actively being worked
- `blocked` → waiting on dependency
- `review` → ready for human review

**GitHub API**:
```bash
PUT /repos/:owner/:repo/issues/123/labels
{
  "labels": ["approved", "in-progress"]
}
```

**GitBucket API**: ❌ Missing - No label management endpoint

**Workaround Required**:
- Manual UI clicks for every label change
- Custom SQL: `INSERT INTO ISSUE_LABEL (user_name, repository_name, issue_id, label_id) VALUES (...)`
- Plugin endpoint

**Impact on AI Agent**:
- Cannot track spec lifecycle phases programmatically
- Manual label management creates synchronization errors
- Breaks workflow gates (approval-gate skill, pr-creation-workflow skill)
- Cannot signal "ready for review" to human reviewers

#### 3. **Assignees Cannot Be Managed via API**

**Workflow Stage**: Review assignment, spec ownership

**Problem**: AI agent needs to:
- Assign reviewer when PR created
- Assign implementer when spec approved
- Reassign when blocked by external dependency

**GitHub API**:
```bash
POST /repos/:owner/:repo/issues/123/assignees
{
  "assignees": ["reviewer-username"]
}
```

**GitBucket API**: ❌ Missing - No assignee management endpoint

**Impact on AI Agent**:
- Cannot assign reviewers programmatically
- Cannot update ownership during workflow
- Breaks multi-developer coordination
- Manual management creates ownership confusion

#### 4. **Autoclose Only Works on Default Branch**

**Workflow Stage**: Dev → Main merge (NOT feature → dev merge)

**Problem**: AI agent uses three-branch workflow where:
- Specs exist as GitHub Issues
- Implementation happens on `feature/spec-123`
- Merge to `dev` for integration testing
- Later merge `dev` to `main` for production release

**Expected Behavior**:
```
1. Create spec issue #123
2. Implement on feature/spec-123
3. Create PR: feature/spec-123 → dev
4. Merge PR → Issues #123 autocloses
```

**GitBucket Behavior**:
```
1. Create spec issue #123
2. Implement on feature/spec-123
3. Create PR: feature/spec-123 → dev
4. Merge PR → ❌ Issue #123 NOT autoclosed (dev ≠ default branch)
5. Manual intervention required
```

**Impact on AI Agent**:
- Breaks automated workflow progression
- Requires manual issue closure after every PR
- Sub-issue tracking broken (parent can't close until manual intervention)
- `git-workflow` skill completion phase fails (cannot verify closure)
- Halts after PR merge waiting for manual close verification

### Concrete Workflow Failure Example

**Scenario**: AI agent implementing spec with sub-issues

**Workflow Definition** (from git-workflow skill):
```
Parent Issue #100: Add OAuth2 authentication
├── Sub-issue #101: Phase 1 - Database schema
├── Sub-issue #102: Phase 2 - API endpoints  
└── Sub-issue #103: Phase 3 - UI components
```

**Expected Flow**:
```
1. #100 approved → Authorization cascades to all sub-issues
2. Create branch feature/oauth2 from dev
3. Implement Phase 1 → PR to dev → Merge → Autoclose #101
4. Implement Phase 2 → PR to dev → Merge → Autoclose #102
5. Implement Phase 3 → PR to dev → Merge → Autoclose #103
6. All sub-issues closed → Close parent #100
```

**Actual Flow with GitBucket Deficiencies**:
```
1. #100 approved → Authorization cascades
2. Create branch feature/oauth2 from dev
3. Implement Phase 1 → PR to dev → Merge 
   → ❌ #101 NOT autoclosed (dev ≠ main)
   → AI waits for manual close
   → HALT - cannot proceed to Phase 2
4. Human manually closes #101
5. Implement Phase 2 → PR → Merge
   → ❌ #102 NOT autoclosed
   → HALT again
...repeat for each phase
```

**Result**: 
- **Workflow broken at every merge**
- **Human intervention required N+1 times** (once per sub-issue + cleanup)
- **AI agent cannot complete uninterrupted workflow**
- **Spec tracking workflow unusable**

### Why GitHub MCP Tools Fail on GitBucket

GitHub MCP tools (like `github_update_issue`) assume these endpoints exist:

```python
# MCP tool expectation:
github_issue_write(method="update", owner=owner, repo=repo, issue_number=123, state="closed")

# GitHub API call:
PATCH https://api.github.com/repos/:owner/:repo/issues/123
{"state": "closed"}

# GitBucket result:
404 Not Found (or 405 Method Not Allowed)
```

**Failure Modes**:
1. AI agent calls MCP tool → GitBucket returns error
2. Retry logic adds `needs-approval` label → Fails (no label endpoint)
3. Agent cannot signal workflow state changes
4. Workflow gridlocks waiting for impossible API operation

## Plugin Architecture

This plugin provides **MCP-native endpoints** that work alongside GitBucket's existing API:

```
GitBucket Server
├── Standard API (/api/v3/...)
│   └── GitHub-compatible endpoints (with gaps)
└── MCP Plugin
    └── MCP Endpoints (/api/mcp/v1/...)
        ├── PATCH  /issues/:number           → Fix missing issue update
        ├── PUT    /issues/:number/labels    → Fix missing label management
        ├── POST   /issues/:number/assignees → Fix missing assignee management
        └── POST   /hooks/pr-merged          → Plugin hook for autoclose fix
```

### How It Works

1. **Plugin Hooks**: Registers GitBucket plugin hooks for PR merge events
2. **Event Processing**: When a PR merges, the plugin:
   - Extracts issue references from PR title, body, and commit messages
   - Closes referenced issues **regardless of target branch**
   - Bypasses the `defaultBranch` restriction in native code
3. **MCP Endpoints**: Provides additional API endpoints that GitBucket itself doesn't implement
4. **Standard HTTPS**: All endpoints use the same GitBucket server URL and authentication

### MCP Endpoint Design

MCP endpoints follow the same authentication and URL pattern as standard GitBucket API:

```
HTTPS URL: https://your-gitbucket-server/api/mcp/v1/repos/:owner/:repo/issues/:number

Authentication: 
- Same as GitBucket API (token auth, session cookie)
- Uses existing GitBucket security context
- Respects repository permissions

Request/Response Format:
- GitHub-compatible JSON format
- Same error structures
- Same pagination patterns
```

## Installation

### Prerequisites

- GitBucket 4.x+
- GitBucket plugin system enabled
- Server admin access

### Build from Source

```bash
git clone https://github.com/NewsRx/gitbucket-mcp-plugin.git
cd gitbucket-mcp-plugin
sbt package
```

The plugin JAR will be in `target/scala-2.13/gitbucket-mcp-plugin_*.jar`.

### Install

1. Copy JAR to GitBucket plugins directory: `$GITBUCKET_HOME/plugins/`
2. Restart GitBucket
3. Verify plugin loaded: Check GitBucket logs for `MCP Plugin loaded`

### Configuration

Plugin configuration in `$GITBUCKET_HOME/plugins/mcp-plugin.conf`:

```hocon
mcp-plugin {
  # Enable autoclose fix (default: true)
  autoclose.enabled = true
  
  # Enable MCP API endpoints (default: true)
  api.enabled = true
  
  # Enable PATCH /issues/:number endpoint (default: true)
  api.issue-update = true
  
  # Enable PUT /issues/:number/labels endpoint (default: true)
  api.label-management = true
  
  # Enable POST /issues/:number/assignees endpoint (default: true)
  api.assignee-management = true
}
```

## API Endpoints

### PATCH /api/mcp/v1/repos/:owner/:repo/issues/:number

Update an issue's title, body, or state.

**Request:**
```json
{
  "title": "Updated issue title",
  "body": "Updated description",
  "state": "closed"
}
```

**Response:**
```json
{
  "id": 123,
  "number": 42,
  "title": "Updated issue title",
  "body": "Updated description",
  "state": "closed",
  ...
}
```

### PUT /api/mcp/v1/repos/:owner/:repo/issues/:number/labels

Replace all labels on an issue.

**Request:**
```json
{
  "labels": ["bug", "priority-high", "needs-review"]
}
```

### POST /api/mcp/v1/repos/:owner/:repo/issues/:number/assignees

Add assignees to an issue.

**Request:**
```json
{
  "assignees": ["user1", "user2"]
}
```

### DELETE /api/mcp/v1/repos/:owner/:repo/issues/:number/assignees

Remove assignees from an issue.

## Autoclose Behavior

### How It Fixes the Limitation

1. **Registers Hook**: Plugin registers `PullRequestHooks.merged()` callback
2. **Intercepts Merge Event**: When any PR merges, the hook fires
3. **Extracts References**: Parses PR title, body, and all commit messages for:
   - `fix #N`, `fixes #N`, `fixed #N`
   - `close #N`, `closes #N`, `closed #N`
   - `resolve #N`, `resolves #N`, `resolved #N`
4. **Closes Issues**: Calls `IssuesService.closeIssuesFromMessage()` directly - bypassing branch check
5. **Posts Comments**: Adds "Closed by PR #X" comment on each closed issue

### Testing Autoclose

After installing the plugin:

1. Create PR targeting any branch (not just default)
2. Add `Fixes #123` to PR description
3. Merge the PR
4. Verify issue #123 is closed with comment "Closed by PR #..."

## Why Plugin Instead of MCP Workaround?

An alternative would be an MCP server that intercepts API calls. However:

| Approach | Pros | Cons |
|----------|------|------|
| **GitBucket Plugin** | Native integration, direct service access, server-side deployment | Requires GitBucket admin access |
| **MCP Server Workaround** | No GitBucket changes needed | Hack layer, can't fix all issues, brittle |

**Plugin Benefits**:
- Direct access to `IssuesService.updateClosed()`
- Can modify autoclose behavior at source

**MCP Server Limitations**:
- Would need to intercept ALL GitHub API calls
- Cannot change GitBucket's internal behavior (defaultBranch check)
- Would still fail on autoclose for non-default branches

**Decision**: Plugin is the correct solution. MCP servers cannot fix server-side logic.

## Development

### Prerequisites

- Scala 2.13.x
- SBT 1.x
- JDK 11+
- GitBucket source for reference

### Build

```bash
sbt compile
```

### Test

```bash
sbt test
```

### Package

```bash
sbt package
```

### Release

```bash
sbt release
```

## Project Structure

```
gitbucket-mcp-plugin/
├── src/main/scala/gitbucket/mcpplugin/
│   ├── MCPPlugin.scala              # Plugin entry point
│   ├── AutocloseHook.scala          # PR merge hook
│   ├── api/
│   │   ├── IssueUpdateEndpoint.scala # PATCH /issues/:number
│   │   ├── LabelEndpoint.scala       # PUT /issues/:number/labels
│   │   └── AssigneeEndpoint.scala    # POST/DELETE assignees
│   └── AuthFilter.scala              # Reuse GitBucket auth
├── src/test/scala/                  # Tests
├── project/                         # SBT build config
└── README.md
```

## Security Considerations

- **Authentication**: Uses GitBucket's existing authentication layer
- **Authorization**: Respects repository permissions (read/write/admin)
- **CSRF Protection**: Inherits GitBucket CSRF tokens for web requests
- **API Tokens**: Supports GitBucket personal access tokens
- **No Bypass**: Plugin does NOT bypass permission checks

## Compatibility

- GitBucket 4.42.0+
- Scala 2.13+
- SBT 1.x
- JVM 11+

## License

Apache License 2.0 (compatible with GitBucket's Apache 2.0 license)

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/improvement`)
3. Make changes in **Scala** (not Java)
4. Run tests (`sbt test`)
5. Commit (`git commit -am 'Add some feature'`)
6. Push (`git push origin feature/improvement`)
7. Create Pull Request

## Credits

- GitBucket - The underlying platform this plugin extends
- Apache License 2.0 - License compatibility
- NewsRx - Plugin development and maintenance