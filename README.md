# GitBucket MCP Plugin

MCP (Model Context Protocol) plugin for GitBucket that enables AI agents to manage projects programmatically.

## Purpose

Provides API endpoints for MCP servers to interact with GitBucket:

- Create, update, close issues
- Manage labels and assignees  
- Automate issue closure on PR merge
- Track implementation through issue hierarchies

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

## API Endpoints

```
PATCH /api/mcp/v1/repos/:owner/:repo/issues/:number
PUT   /api/mcp/v1/repos/:owner/:repo/issues/:number/labels  
POST  /api/mcp/v1/repos/:owner/:repo/issues/:number/assignees
```

## Autoclose

Issues referenced in PR descriptions close automatically on merge to any branch:

```
Fixes #123
Closes #456
Resolves #789
```

## License

Apache 2.0