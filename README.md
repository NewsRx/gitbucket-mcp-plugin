# GitBucket AI Agent Enablement Plugin

Enables AI agents to manage GitBucket projects using MCP tools designed for GitHub.

## Purpose

This plugin provides GitBucket compatibility for AI agents using GitHub-based MCP tools:

- **Spec Planning**: Create, update, close issues programmatically
- **Workflow Tracking**: Manage issue states, labels, assignees
- **Sub-issue Management**: Track multi-phase implementations via issue hierarchies
- **PR Integration**: Automatic issue closure on merge across all branches

AI agents use GitHub MCP tools (like `github_issue_write`, `github_create_pull_request`) against GitBucket. This plugin provides the missing API endpoints and workflow automation.

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/gitbucket-mcp-plugin-0.1.0.jar`

## Install

```bash
cp build/libs/gitbucket-mcp-plugin-0.1.0.jar $GITBUCKET_HOME/plugins/
```

Restart GitBucket.

## AI Agent Workflow Support

### Issue Lifecycle Management

Create, update, and close issues during implementation:

- PATCH `/api/v3/repos/:owner/:repo/issues/:number` - Update issue state, title, body
- PUT `/api/v3/repos/:owner/:repo/issues/:number/labels` - Add/remove workflow labels
- POST `/api/v3/repos/:owner/:repo/issues/:number/assignees` - Assign reviewers/owners

### Spec Tracking

Enable AI agents to track specs via issues:

- Create parent specification issues
- Link sub-issues for implementation phases
- Close parent when all phases complete
- Use labels for workflow states (`needs-approval`, `approved`, `in-progress`, `review`)

### Three-Branch Workflow

Support for `feature/*` → `dev` → `main` workflow:

- Merge PR to any branch triggers autoclose
- Issue references in PR descriptions work on `dev` and feature branches
- Sub-issues close automatically when implementation merges

### MCP Tool Compatibility

AI agents using GitHub MCP tools can interact with GitBucket as if it were GitHub:

```python
# AI agent code - works for both GitHub and GitBucket
github_issue_write(method="update", issue_number=123, state="closed")
github_create_pull_request(title="Fix bug", head="feature/fix", base="dev")
```

## Configuration

Plugin works out of the box. Optional configuration in `$GITBUCKET_HOME/plugins/mcp-plugin.conf`:

```hocon
mcp-plugin {
  autoclose.enabled = true
  api.endpoints.enabled = true
}
```

## License

Apache 2.0