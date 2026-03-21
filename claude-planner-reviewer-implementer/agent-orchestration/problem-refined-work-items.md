# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Split the finished ScrollShield technical implementation spec (`InputData/scrollshield-initial-plan`) into smaller, agent-sized work items. Each work item should be a self-contained plan that an individual agent can implement in a single session. Place all new work item files in `InputData/RefinedWorkItems/`.

## Context Files

- InputData/scrollshield-initial-plan

## Target Files (to modify)

- InputData/RefinedWorkItems/

## Rules & Constraints

- Do not modify `InputData/scrollshield-initial-plan`

## Review Criteria

1. Completeness — Every section/module from the original plan is covered by at least one work item
2. No content loss — No requirements, details, or specifications from the original plan are dropped
3. Right-sized items — Each work item is small enough for a single agent to implement in one session
4. Self-contained — Each work item has enough context to be implemented independently
5. Clear dependencies — Inter-item dependencies and build order are explicitly stated
6. Consistent format — All work items follow the same structure/template
7. Actionable — Each work item specifies exact changes, not vague directions
8. No overlap — Work items don't duplicate effort or conflict with each other
9. Traceability — Each work item references which part of the original plan it comes from
10. Implementation-ready — A developer could pick up any single work item and start coding

## Implementation Instructions

No build commands — this is a document-only task.
