# GitBucket MCP Plugin

MCP (Model Context Protocol) server for GitBucket. Enables AI agents to communicate with GitBucket via MCP protocol.

## GitBucket Compatibility

**Supported Versions:** GitBucket 4.42.0 - 4.46.0

| GitBucket Version | Plugin Compatibility | Notes |
|------------------|---------------------|-------|
| 4.46.0 | ✅ Full | Current target |
| 4.45.0 | ✅ Full | Compatible |
| 4.44.0 | ✅ Full | Compatible |
| 4.43.0 | ✅ Full | H2 database migration (DB layer only) |
| 4.42.0 | ✅ Full | Minimum version - Java 17 required |
| < 4.42.0 | ❌ Incompatible | Java 11 not supported |

**Compatility Verified By:**
- Plugin.scala trait unchanged between 4.42.0 and 4.46.0
- All plugin hook signatures stable across versions
- MergeService.scala `closeIssuesFromMessage()` pattern stable

**Breaking Changes (Historical):**
- 4.43.0: H2 database 1.x → 2.x migration (DB layer, not Plugin API)
- 4.42.0: Java 17 requirement (JDK 11 support dropped)

## What This Does

Exposes GitBucket's issue tracking and project management capabilities through MCP:

- MCP tools for issue operations (create, update, close, search)
- MCP resources for repository and issue data access  
- MCP prompts for common GitBucket workflows

AI agents connect via MCP (SSE or stdio), not REST API.

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

## MCP Protocol

This plugin implements MCP server capabilities:

- **Tools**: Issue CRUD operations, label management, assignee assignment
- **Resources**: Repository listing, issue search, file contents
- **Prompts**: Workflow templates for spec planning and implementation tracking

AI agents discover and invoke these capabilities through MCP protocol negotiation.

## Known Issues

**GitBucket API Deficiencies (4.42.1 - 4.46.0):**
- `update_issue()` PATCH endpoint returns 404 (not implemented)
- `add_labels_to_issue()` POST returns empty array, labels not added
- `replace_issue_labels()` PUT returns empty array, labels not set

**Workaround:** Add labels during issue creation only. Label management after creation requires GitBucket web UI.

## License

Apache 2.0