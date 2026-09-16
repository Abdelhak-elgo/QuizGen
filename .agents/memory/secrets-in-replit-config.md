---
name: Secrets in Replit configuration
description: Prevent environment credentials from being committed through tracked Replit configuration.
---

Never store credentials under user environment sections in tracked Replit
configuration. Use Replit Secrets and audit configuration diffs plus repository
history for secret variable names before pushing.

**Why:** An automatic configuration checkpoint can make a locally configured
credential part of Git history even when later Git commands use an ephemeral
credential helper.

**How to apply:** Before every remote push that used a project credential,
confirm the credential key is absent from tracked configuration and add
automated secret scanning to CI.