# GitBucket MCP Plugin

Fixes API deficiencies and autoclose limitations.

## Build

```bash
sbt assembly
```

Output: `target/scala-2.13/gitbucket-mcp-plugin-assembly-0.1.0.jar`

## Install

```bash
cp target/scala-2.13/gitbucket-mcp-plugin-assembly-0.1.0.jar $GITBUCKET_HOME/plugins/
```

Restart GitBucket.

## What This Fixes

- Autoclose issues from PRs to non-default branches (feature → dev)
- `PATCH /api/v3/repos/:owner/:repo/issues/:number` - update issue state
- `PUT /api/v3/repos/:owner/:repo/issues/:number/labels` - manage labels
- `POST /api/v3/repos/:owner/:repo/issues/:number/assignees` - manage assignees

## License

Apache 2.0