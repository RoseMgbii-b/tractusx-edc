# JIRA Structure for Custom Fork Management

This document outlines the JIRA structure for managing custom forks that will be pushed to a centralized repository for all developers working on the Tractus-X EDC project.

## 1. Project Structure

### Project Key: `EDC-FORKS`
**Project Name:** Tractus-X EDC Custom Fork Management

### Project Type
- **Software Development** (Scrum or Kanban board recommended)

---

## 2. Issue Types

### Epic
- **Purpose:** Group related fork work and features
- **Naming Convention:** `[FORK-EPIC-XXX] Feature/Component Name`
- **Examples:**
  - `[FORK-EPIC-001] Control Plane Custom Extensions`
  - `[FORK-EPIC-002] Data Plane Enhancements`
  - `[FORK-EPIC-003] Integration Testing Framework`

### Story
- **Purpose:** User-facing features or enhancements from forks
- **Naming Convention:** `[FORK-XXX] As a [role], I want [feature] so that [benefit]`
- **Acceptance Criteria:** Must include:
  - Fork source information
  - Integration plan
  - Testing requirements
  - Documentation updates

### Task
- **Purpose:** Technical work items for fork integration
- **Examples:**
  - Code review and merge
  - Conflict resolution
  - Dependency updates
  - Build configuration changes

### Bug
- **Purpose:** Issues discovered during fork integration
- **Priority Levels:** Critical, High, Medium, Low

### Sub-task
- **Purpose:** Break down complex stories/tasks
- **Use Cases:**
  - Individual file merges
  - Component-specific testing
  - Documentation sections

---

## 3. Workflow States

### Standard Workflow
```
Backlog → To Do → In Progress → Code Review → Testing → Done
                                    ↓
                                Blocked
```

### State Definitions

1. **Backlog**
   - New fork requests submitted
   - Awaiting prioritization

2. **To Do**
   - Prioritized and ready to start
   - Assigned to developer/team

3. **In Progress**
   - Developer actively working on fork integration
   - Code changes in progress

4. **Code Review**
   - PR created and submitted
   - Awaiting reviewer approval
   - Address review comments

5. **Testing**
   - Code approved, running tests
   - Integration testing
   - QA validation

6. **Blocked**
   - Waiting on dependencies
   - Conflicts with other work
   - External blockers

7. **Done**
   - Merged to centralized repo
   - All tests passing
   - Documentation updated

---

## 4. Custom Fields

### Required Fields

#### Fork Information
- **Fork Source URL** (URL field)
  - Link to the original fork repository
- **Fork Owner** (User picker)
  - Developer who created the fork
- **Fork Branch Name** (Text field)
  - Branch name in the fork
- **Target Branch** (Select list)
  - `main`, `develop`, `release/X.X.X`
- **Fork Creation Date** (Date picker)
  - When the fork was created

#### Integration Details
- **Integration Complexity** (Select list)
  - `Low`, `Medium`, `High`, `Critical`
- **Estimated Merge Time** (Number field)
  - Hours estimated for integration
- **Conflicts Detected** (Checkbox)
  - Whether merge conflicts exist
- **Dependencies** (Multi-select)
  - Related issues/tickets
- **Affected Components** (Multi-select)
  - `edc-controlplane`, `edc-dataplane`, `edc-extensions`, `core`, `spi`, etc.

#### Quality Gates
- **Tests Passing** (Checkbox)
- **Code Review Status** (Select list)
  - `Not Started`, `In Progress`, `Approved`, `Changes Requested`
- **Documentation Updated** (Checkbox)
- **Breaking Changes** (Checkbox)
- **Migration Guide Required** (Checkbox)

#### Tracking
- **PR Number** (Text field)
  - GitHub PR reference
- **PR Link** (URL field)
  - Link to pull request
- **Merge Date** (Date picker)
  - When merged to centralized repo
- **Merge Commit SHA** (Text field)
  - Git commit hash

---

## 5. Labels

### Component Labels
- `controlplane`
- `dataplane`
- `extensions`
- `core`
- `spi`
- `build`
- `docs`
- `testing`

### Priority Labels
- `priority-critical`
- `priority-high`
- `priority-medium`
- `priority-low`

### Type Labels
- `feature`
- `bugfix`
- `enhancement`
- `refactor`
- `performance`
- `security`

### Status Labels
- `needs-review`
- `needs-testing`
- `conflicts`
- `ready-to-merge`
- `blocked`

### Team Labels
- `team-backend`
- `team-frontend`
- `team-devops`
- `team-qa`

---

## 6. Components

Create components that map to repository structure:

1. **Control Plane**
   - `edc-controlplane-postgresql-hashicorp-vault`
   - `edc-runtime-memory`
   - Other control plane modules

2. **Data Plane**
   - `edc-dataplane-hashicorp-vault`
   - Other data plane modules

3. **Extensions**
   - Custom extensions directory

4. **Core**
   - Core functionality

5. **SPI**
   - Service Provider Interfaces

6. **Build & CI/CD**
   - Gradle configurations
   - CI/CD pipelines

7. **Documentation**
   - Docs updates

8. **Testing**
   - Test framework
   - Test utilities

---

## 7. Epics Structure

### Epic Template

**Epic Name:** `[Component/Feature] Custom Fork Integration`

**Epic Description:**
```
Fork Source: [URL]
Fork Owner: [Developer Name]
Original Purpose: [Why was this fork created]
Integration Goal: [What we want to achieve]
Estimated Stories: [Number]
Target Sprint: [Sprint name/number]
```

**Epic Acceptance Criteria:**
- [ ] All stories completed
- [ ] All code merged to centralized repo
- [ ] All tests passing
- [ ] Documentation updated
- [ ] Code review completed
- [ ] No breaking changes (or migration guide provided)

---

## 8. Story Template

### Story Format

**Title:** `[FORK-XXX] Integrate [Feature Name] from [Developer] Fork`

**Description:**
```markdown
## Fork Information
- **Fork Source:** [GitHub URL]
- **Fork Owner:** @username
- **Original Branch:** branch-name
- **Target Branch:** main/develop

## Feature Description
[Detailed description of what this fork adds/changes]

## Changes Overview
- [List of major changes]
- [Files/modules affected]

## Integration Plan
1. [Step 1]
2. [Step 2]
3. [Step 3]

## Testing Strategy
- [Unit tests]
- [Integration tests]
- [Manual testing scenarios]

## Dependencies
- Related issues: [JIRA-XXX, JIRA-YYY]
- Blocks: [JIRA-ZZZ]
- Blocked by: [JIRA-AAA]
```

**Acceptance Criteria:**
- [ ] Fork code reviewed and approved
- [ ] All merge conflicts resolved
- [ ] Code follows project style guide
- [ ] All tests passing (unit + integration)
- [ ] No new dependencies without approval
- [ ] Documentation updated
- [ ] PR follows conventional commits format
- [ ] Code review completed by committer
- [ ] Merged to target branch

---

## 9. Task Template

### Integration Task Format

**Title:** `[FORK-XXX] [Action] - [Component/File]`

**Description:**
```markdown
## Task Details
- **Parent Story:** [FORK-XXX]
- **Component:** [Component name]
- **Files Affected:** [List of files]

## Steps
1. [ ] Step 1
2. [ ] Step 2
3. [ ] Step 3

## Notes
[Any additional information]
```

---

## 10. Board Configuration

### Scrum Board Setup

**Columns:**
1. **Backlog** - All unprioritized fork requests
2. **To Do** - Ready to start
3. **In Progress** - Active development
4. **Code Review** - PR submitted, awaiting review
5. **Testing** - Code approved, testing in progress
6. **Done** - Merged and complete

### Filters

**Quick Filters:**
- `My Forks` - Issues assigned to me
- `Needs Review` - In code review state
- `Blocked` - Currently blocked
- `High Priority` - Critical/high priority forks
- `This Sprint` - Current sprint items

**Saved Filters:**
- `All Active Forks` - All issues not in Done
- `Conflicts Detected` - Forks with merge conflicts
- `Ready to Merge` - Approved and tested, ready for merge
- `By Component` - Filter by component label

---

## 11. Automation Rules

### Recommended Automations

1. **Auto-assign on creation**
   - Assign to fork owner if available
   - Or assign to team lead for triage

2. **Status transitions**
   - Move to "Code Review" when PR link added
   - Move to "Testing" when PR approved
   - Move to "Done" when merge commit SHA added

3. **Notifications**
   - Notify assignee when moved to "In Progress"
   - Notify reviewers when moved to "Code Review"
   - Notify team when moved to "Done"

4. **Field updates**
   - Auto-populate "Fork Creation Date" from Git
   - Update "Integration Complexity" based on affected components

---

## 12. Reporting & Metrics

### Useful Reports

1. **Fork Integration Velocity**
   - Stories completed per sprint
   - Average time from "To Do" to "Done"

2. **Fork Backlog Health**
   - Number of forks in backlog
   - Age of oldest fork request
   - Blocked forks count

3. **Component Impact**
   - Most affected components
   - Integration complexity distribution

4. **Developer Contribution**
   - Forks by developer
   - Integration success rate

---

## 13. Best Practices

### For Fork Submitters

1. **Before Creating JIRA Issue:**
   - Ensure fork is up-to-date with upstream
   - Resolve any obvious conflicts locally
   - Run all tests and ensure they pass
   - Document the purpose and changes

2. **When Creating Story:**
   - Provide complete fork information
   - Link to original issue/requirement (if exists)
   - Include screenshots/demos for UI changes
   - List all dependencies and breaking changes

3. **During Integration:**
   - Keep JIRA updated with progress
   - Link PR as soon as created
   - Respond promptly to review comments
   - Update status as work progresses

### For Reviewers

1. **Review Checklist:**
   - Code follows style guide
   - Tests are comprehensive
   - No unnecessary dependencies
   - Documentation is updated
   - No breaking changes (or migration guide)

2. **Timeline:**
   - Complete reviews within 2 business days
   - Use GitHub suggestions for minor changes
   - Be constructive and objective

### For Project Managers

1. **Prioritization:**
   - Review fork backlog regularly
   - Prioritize based on:
     - Business value
     - Dependencies
     - Technical debt impact
     - Developer availability

2. **Tracking:**
   - Monitor blocked forks
   - Track integration velocity
   - Identify bottlenecks

---

## 14. Integration with GitHub

### GitHub Integration Setup

1. **JIRA-GitHub Integration:**
   - Install JIRA GitHub integration
   - Link repositories
   - Enable automatic issue linking

2. **Branch Naming:**
   - Use format: `fork/[JIRA-KEY]-[short-description]`
   - Example: `fork/FORK-123-controlplane-enhancement`

3. **Commit Messages:**
   - Include JIRA key: `[FORK-123] Description`
   - Follow conventional commits format
   - Link to parent story if sub-task

4. **PR Template:**
   ```markdown
   ## JIRA Ticket
   [FORK-XXX](link-to-jira)

   ## Description
   [Description from JIRA]

   ## Changes
   - [ ] Change 1
   - [ ] Change 2

   ## Testing
   - [ ] Unit tests passing
   - [ ] Integration tests passing

   ## Checklist
   - [ ] Code follows style guide
   - [ ] Documentation updated
   - [ ] No breaking changes
   ```

---

## 15. Example Workflow

### Complete Fork Integration Flow

1. **Developer creates fork** → Works locally
2. **Developer creates JIRA Story** → Provides all fork details
3. **PM/Lead prioritizes** → Moves to "To Do", assigns developer
4. **Developer starts work** → Moves to "In Progress"
5. **Developer creates PR** → Adds PR link, moves to "Code Review"
6. **Reviewer reviews** → Comments/approves
7. **If changes needed** → Developer updates, re-requests review
8. **If approved** → Moves to "Testing"
9. **Tests run** → CI/CD validates
10. **If tests pass** → Merged, add commit SHA, move to "Done"
11. **If tests fail** → Move back to "In Progress", fix issues

---

## 16. Quick Reference

### Issue Creation Checklist

- [ ] Fork source URL provided
- [ ] Fork owner identified
- [ ] Target branch specified
- [ ] Affected components listed
- [ ] Integration complexity assessed
- [ ] Dependencies identified
- [ ] Acceptance criteria defined
- [ ] Assigned to appropriate developer

### Status Transition Checklist

**To Do → In Progress:**
- [ ] Developer assigned
- [ ] Fork cloned locally
- [ ] Initial conflict check done

**In Progress → Code Review:**
- [ ] PR created
- [ ] PR link added to JIRA
- [ ] All local tests passing

**Code Review → Testing:**
- [ ] Code review approved
- [ ] All review comments addressed
- [ ] PR ready for merge

**Testing → Done:**
- [ ] All CI/CD tests passing
- [ ] Merged to target branch
- [ ] Merge commit SHA added
- [ ] Documentation updated

---

## 17. Contact & Support

For questions about this JIRA structure:
- **Project Admin:** [Contact Info]
- **JIRA Admin:** [Contact Info]
- **Team Lead:** [Contact Info]

---

**Document Version:** 1.0  
**Last Updated:** [Date]  
**Maintained by:** Tractus-X EDC Team



