#!/bin/bash

# Claude Flow Production Ready Development Script
# This script launches claude-flow with optimal settings for making the WildFly Flyway subsystem production-ready

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}WildFly Flyway Production Ready Builder${NC}"
echo -e "${BLUE}========================================${NC}"

# Change to project directory
cd "$(dirname "$0")"

# Option 1: Interactive mode with swarm optimization (RECOMMENDED)
echo -e "\n${GREEN}Option 1: Interactive Swarm Mode (Recommended)${NC}"
echo "Command: npx claude-flow@alpha --prompt claude-flow-production-ready.md --agents 8"
echo -e "${YELLOW}This will spawn 8 specialized agents working in parallel on different aspects${NC}"

# Option 2: Batch mode for CI/CD
echo -e "\n${GREEN}Option 2: Batch Mode (CI/CD)${NC}"
echo "Command: npx claude-flow@alpha batch --prompt claude-flow-production-ready.md --output production-report.md"

# Option 3: Focused development mode
echo -e "\n${GREEN}Option 3: Focused Mode (Specific Tasks)${NC}"
echo "Examples:"
echo "  # Focus on testing:"
echo "  npx claude-flow@alpha --prompt claude-flow-production-ready.md --focus testing --agents 4"
echo ""
echo "  # Focus on security:"
echo "  npx claude-flow@alpha --prompt claude-flow-production-ready.md --focus security --agents 4"
echo ""
echo "  # Focus on documentation:"
echo "  npx claude-flow@alpha --prompt claude-flow-production-ready.md --focus documentation --agents 3"

# Option 4: Full production pipeline
echo -e "\n${GREEN}Option 4: Full Production Pipeline${NC}"
echo "Command: npx claude-flow@alpha pipeline --config production-pipeline.yaml"

# Actual execution (uncomment the preferred option)
echo -e "\n${BLUE}Executing Option 1 (Interactive Swarm Mode)...${NC}\n"

# Run with 8 agents for comprehensive parallel development
npx claude-flow@alpha \
  --prompt claude-flow-production-ready.md \
  --agents 8 \
  --topology hierarchical \
  --strategy balanced \
  --memory persistent \
  --neural-training enabled \
  --auto-spawn true \
  --bottleneck-analysis true \
  --telemetry true

# Alternative: Run with specific configuration file
# npx claude-flow@alpha --config .claude-flow.production.json

# Alternative: Run in watch mode for continuous development
# npx claude-flow@alpha watch --prompt claude-flow-production-ready.md --agents 6