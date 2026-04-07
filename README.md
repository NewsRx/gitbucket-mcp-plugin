# GitBucket MCP Plugin

MCP (Model Context Protocol) server for GitBucket. Enables AI agents to communicate with GitBucket via MCP protocol.

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

## License

Apache 2.0