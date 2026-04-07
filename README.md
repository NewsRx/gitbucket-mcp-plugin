# GitBucket MCP Plugin

A GitBucket plugin that fixes API deficiencies and autoclose limitations via MCP (Model Context Protocol) integration.

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

## Why MCP Instead of GitBucket Extension?

An alternative approach would be to submit changes upstream to GitBucket. However:

| Approach | Pros | Cons |
|----------|------|------|
| **GitBucket PR Upstream** | Benefits all users, long-term solution | Slow review cycle, may be rejected, requires coordination |
| **MCP Plugin** | Immediate fix, full control, works now | Requires plugin deployment, maintenance burden |

**Decision**: This plugin is the immediate solution. Upstream PRs are welcome as a long-term fix.

## Development

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
3. Make changes
4. Run tests (`sbt test`)
5. Commit (`git commit -am 'Add some feature'`)
6. Push (`git push origin feature/improvement`)
7. Create Pull Request

## Credits

- GitBucket - The underlying platform this plugin extends
- Apache License 2.0 - License compatibility
- NewsRx - Plugin development and maintenance