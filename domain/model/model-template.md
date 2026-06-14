# [Entity Name]

## Overview

Brief description of the entity's purpose and its role within the domain.

## Properties

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| id | uuid | yes | Unique identifier |
| createdAt | datetime | yes | Timestamp of creation |
| updatedAt | datetime | yes | Timestamp of last update |

## Behaviors

- **[BehaviorName]**: Description of what the behavior does and any side effects.

## Events

- **[EntityEventName]**: Emitted when [condition]. Payload: [key fields].

## Relationships

- **[RelatedEntity]** (`[cardinality]`): Description of the relationship.

## Business Rules

- [Invariant or constraint that must always hold.]
