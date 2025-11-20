---
description: Create git commit without Claude Code signature
tags: [git, commit]
---

You are creating a git commit for the current changes.

# Instructions

1. Run these commands in parallel:
   - `git status` to see all untracked files
   - `git diff` to see both staged and unstaged changes
   - `git log -5 --oneline` to see recent commit style

2. Analyze the changes and draft a concise commit message (1-2 sentences) that:
   - Describes the nature of the changes (new feature, enhancement, bug fix, refactoring, etc.)
   - Focuses on the "why" rather than the "what"
   - Follows the repository's commit message style
   - **CRITICAL**: Does NOT include any Claude Code signature or Co-Authored-By line

3. Add relevant files and create the commit:
   - Add untracked/modified files to staging
   - Create commit with clean message (NO signature)
   - Run `git status` after to verify

# Important Notes

- Do NOT include "🤖 Generated with Claude Code" signature
- Do NOT include "Co-Authored-By: Claude" line
- Do NOT push unless explicitly asked
- Do NOT commit files with secrets (.env, credentials.json, etc)
- Keep the commit message clean and professional
