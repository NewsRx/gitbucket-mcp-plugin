# GitBucket MCP Plugin

Enables AI agents to manage GitBucket projects using MCP (Model Context Protocol).

## Purpose

Provides MCP endpoints for AI agents to:

- Plan and track implementation through GitBucket issues
- Manage issue lifecycle (create, update, close)
- Track multi-phase specs via sub-issue hierarchies
- Control workflow states with labels and assignees
- Automate issue closure when PRs merge to any branch

AI agents use MCP tools to interact with GitBucket programmatically during software development workflows.

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

## MCP Endpoints

### Issue Management

```
PATCH /api/mcp/v1/repos/:owner/:repo/issues/:number
PUT   /api/mcp/v1/repos/:owner/:repo/issues/:number/labels
POST  /api/mcp/v1/repos/:owner/:repo/issues/:number/assignees
```

### Workflow Automation

- Autoclose issues from PRs merging to any branch (not just default)
- Extract close keywords from PR title, body, and commit messages

## AI Agent Workflows Supported

- Spec-driven development with issue tracking
- Three-branch workflow (`feature/*` → `dev` → `main`)
- Sub-issue hierarchies for multi-phase implementations
- Label-based workflow state management

## License

Apache 2.0