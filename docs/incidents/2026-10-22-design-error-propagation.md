# Design Error Propagation Across Module Boundaries

**Date:** 2026-10-22
**Type:** AI-assisted development process failure

## What Happened

During GUI integration, an agent modified the public API of the Collector
module, introducing an operation-specific progress callback into the
constructor of reusable `LinkedInApplication`. The same change added the
callback to the constructor of `WorkflowOrchestrator`.

This design was subsequently copied into the application-wide `DriverApi`
facade and later extended with another constructor callback for GUI Batch
progress.

The propagation chain was Collector -> Orchestrator -> DriverApi -> additional
Batch progress callback. A local design error became a shared convention
across the collection, orchestration, API, and GUI integration code. It
survived multiple development stages and independent reviews.

**Key commits:**

- `0987016c` - GUI integration introduced constructor callbacks in Collector
  and Orchestrator.
- `030ff8ca` - Propagated the design into `DriverApi`.
- `529aa261` - Extended the facade constructor for GUI Batch progress.

## Initial Design Error

A progress callback belonging to an individual operation was bound to the
lifetime of a reusable service. Running another operation with a different
callback required creating another service instance. As the design spread,
shared service constructors accumulated parameters belonging to particular
operations and their GUI callers.

A constructor callback can be appropriate for a permanent listener covering
all operations of that service. Here, the callback represented the progress
consumer for a particular invocation.

The initial mistake provided the starting point. The broader incident was
its propagation across module boundaries and its repeated acceptance as an
established design.

## Root Cause

Two process failures contributed to the incident:

1. **Uncontrolled cross-module design changes.** An agent working on GUI
   integration made a significant change to another module's public API
   without raising it for architectural review. A local implementation
   decision thus became a shared architectural decision.
2. **Uncritical reuse and review of existing design.** Subsequent agents
   treated the existing implementation as an authoritative precedent and
   propagated it into higher-level APIs. Independent reviews checked the
   implementation of the accepted contract without reassessing the contract
   itself, including which parameters belonged to the service and which
   belonged to an individual invocation.

## Preventive Measures

1. **Module ownership:** Significant architectural changes outside an agent's
   assigned scope require prior approval. Routine integration changes do not.
2. **Independent design judgment:** Existing code is not proof of good design.
   New or extended public API contracts must be evaluated on their own
   architectural merits.

## Lesson Learned

**An architectural mistake becomes a systemic problem when agents can
propagate it across module boundaries and independent reviews repeatedly
validate the same unexamined assumptions.**
