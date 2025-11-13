# Checkstyle Issue Resolution Guide

This document explains how to interpret Checkstyle reports, highlights common violations using the sample XML provided by the user, and documents the standards we follow when fixing them.

## Reading the Checkstyle Report

1. Identify each `<file>` entry and note the `name` attribute. This is the file you need to inspect.
2. For every `<error>` element, collect:
   - `line` and `column` numbers to locate the problem
   - `message` for a human-readable summary
   - `source` to understand which Checkstyle rule triggered the violation
3. Address the errors from highest severity to lowest. Checkstyle treats all items in the XML as errors when run in build pipelines, so aim to resolve every entry.

### Sample Report

```xml
<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="11.0.1">
  <file name="edc-extensions/user-management/src/main/resources/META-INF/services/org.eclipse.edc.spi.system.ServiceExtension">
    <error line="1" severity="error" message="Missing a header - not enough lines in file." source="com.puppycrawl.tools.checkstyle.checks.header.RegexpHeaderCheck"/>
  </file>
  <file name="edc-extensions/user-management/src/main/java/org/eclipse/tractusx/edc/usermanagement/api/RoleDto.java">
    <error line="33" column="1" severity="error" message="Comment has incorrect indentation level 0, expected is 4, indentation should be the same level as line 35." source="com.puppycrawl.tools.checkstyle.checks.indentation.CommentsIndentationCheck"/>
  </file>
  <file name="edc-extensions/user-management/src/main/java/org/eclipse/tractusx/edc/usermanagement/api/UserManagementApiController.java">
    <error line="23" column="8" severity="error" message="Unused import - jakarta.ws.rs.DELETE." source="com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck"/>
  </file>
  <file name="edc-extensions/user-management/src/main/java/org/eclipse/tractusx/edc/usermanagement/KeycloakUserService.java">
    <error line="33" column="1" severity="error" message="Wrong lexicographical order for 'org.eclipse.edc.spi.monitor.Monitor' import." source="com.puppycrawl.tools.checkstyle.checks.imports.CustomImportOrderCheck"/>
  </file>
</checkstyle>
```

## How We Resolved the Sample Issues

### 1. Missing Header (RegexpHeaderCheck)
- **Symptom:** File lacks the mandatory Apache-2.0 license header.
- **Fix:** Add the standard header block at the top of the file before any content.
- **Example:**
  - File: `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`
  - Insert the 14-line header followed by an empty line, then list the service implementation.

### 2. Comment Indentation (CommentsIndentationCheck)
- **Symptom:** Comments (including commented-out code) are not aligned with the surrounding code block.
- **Fix:** Match the indentation level of the comment to the statement that would occupy that position. Remove stale commented-out members whenever possible to avoid future violations.
- **Example:**
  - File: `RoleDto.java`
  - Removed unused commented `clientRole` members instead of trying to align them.

### 3. Unused Import (UnusedImportsCheck)
- **Symptom:** An import statement exists but the type is never referenced.
- **Fix:** Delete the unused import and re-run the compiler or IDE to ensure nothing else depends on it.
- **Example:**
  - File: `UserManagementApiController.java`
  - Removed `jakarta.ws.rs.DELETE` because no endpoint used `@DELETE`.

### 4. Import Order (CustomImportOrderCheck)
- **Symptom:** Imports are not sorted according to the agreed order.
- **Fix:** Reorder imports to match the rule. For Tractus-X EDC:
  1. Static imports (if any)
  2. `java.*`, then `javax.*`
  3. Third-party packages (`jakarta.*`, `org.keycloak.*`, etc.) in alphabetical order
  4. Project-specific packages (`org.eclipse.*`)
- **Example:**
  - File: `KeycloakUserService.java`
  - Moved `org.eclipse.edc.spi.monitor.Monitor` before `org.keycloak.*` imports and replaced fully qualified `new java.util.ArrayList<>()` with `new ArrayList<>()` plus an import.

## Generic Resolution Template

Follow this checklist whenever a Checkstyle report is produced:

1. **Run Checkstyle**
   - `./gradlew check` or `./gradlew <module>:check`
   - For quick iteration, use the IDE Checkstyle plugin or run spot-checks on the affected module.

2. **Parse the XML/Console Output**
   - Gather all files with errors.
   - Sort by error severity or build breakage.

3. **Fix by Category**
   - **Headers:** Ensure every `*.java` and service descriptor contains the Apache-2.0 header.
   - **Imports:** Remove unused imports, add missing ones, and sort alphabetically respecting the project’s rules.
   - **Comments:** Align comment indentation with code blocks; delete obsolete commented code.
   - **Javadoc:** For public classes/methods, ensure Javadoc exists when required.
   - **Formatting:** Apply the standard code style (spaces, braces, blank lines).

4. **Re-run Checkstyle**
   - Confirm the report is clean.
   - If new issues appear, repeat the cycle until the report is empty.

5. **Document Changes (Optional but Recommended)**
   - Summarize fixes in commit messages.
   - Reference the Checkstyle rule in the description (e.g., `RegexpHeaderCheck`).

## Commenting Standards

- **Class-Level Comments:** Our Java classes include the standard Apache-2.0 header. Additional class-level Javadoc is optional unless the class is part of a public API, in which case add a summary sentence and any parameter/return tags.
- **Method-Level Comments:** Use Javadoc to explain non-obvious behaviour, public API semantics, and exceptions thrown.
- **Inline Comments:** Keep them aligned with the surrounding code indentation. Prefer clarifying names and refactoring over inline comments, but use them when behaviour cannot be made self-explanatory.

## Quick Reference

| Rule | Trigger | Typical Fix |
|------|---------|-------------|
| `RegexpHeaderCheck` | Missing license header | Copy the standard header block to the top of the file |
| `CommentsIndentationCheck` | Comment not indented with surrounding code | Align comment or delete obsolete comment |
| `UnusedImportsCheck` | Unused `import` statement | Delete the import |
| `CustomImportOrderCheck` | Imports not sorted | Reorder imports alphabetically per group |

Keep this guide in mind when resolving future Checkstyle issues to maintain consistency across the codebase.
