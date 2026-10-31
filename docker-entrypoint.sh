#!/bin/sh
# Attaches the Application Insights Java agent only when an Application Insights resource is attached
# (APPLICATIONINSIGHTS_CONNECTION_STRING set): without it the JVM starts exactly as before.
set -e

if [ -n "$APPLICATIONINSIGHTS_CONNECTION_STRING" ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-javaagent:/opt/applicationinsights/applicationinsights-agent.jar"
  # Cloud role name: the agent's own setting, filled from the service name the platform passes
  if [ -z "$APPLICATIONINSIGHTS_ROLE_NAME" ] && [ -n "$OTEL_SERVICE_NAME" ]; then
    export APPLICATIONINSIGHTS_ROLE_NAME="$OTEL_SERVICE_NAME"
  fi
  if [ -z "$APPLICATIONINSIGHTS_CONFIGURATION_CONTENT" ]; then
    # Agent diagnostics to stdout only: by default it also writes a log file next to the jar, which the non-root user cannot
    config='"selfDiagnostics":{"destination":"console"}'
    # The agent keeps OTEL_RESOURCE_ATTRIBUTES off requests and logs: pass service.version on as the application version
    version=$(printf '%s' "$OTEL_RESOURCE_ATTRIBUTES" | tr ',' '\n' | sed -n 's/^ *service\.version=//p' | head -n 1)
    if [ -n "$version" ]; then
      config="$config,\"customDimensions\":{\"service.version\":\"$version\"}"
    fi
    export APPLICATIONINSIGHTS_CONFIGURATION_CONTENT="{$config}"
  fi
fi

exec "$@"
