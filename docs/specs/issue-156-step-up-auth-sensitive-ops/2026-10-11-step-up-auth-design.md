# Step-up Auth for Sensitive Connector Operations

**Issue:** casehubio/connectors#156
**Branch:** issue-156-step-up-auth-sensitive-ops
**Date:** 2026-10-11

## Problem

Certain connector operations are inherently sensitive — bank payments, purchases, file deletion, contact deletion. An AI agent with `ServiceConnectionProvider` access must not be able to both initiate AND confirm these operations. Platform#561 shipped the `OperationConfirmation` SPI providing an out-of-band human-in-the-loop gate. Connectors needs to annotate sensitive methods so the platform's decorator processor can enforce confirmation at runtime.

## Design

### Annotations on SPI Interface Methods

Add `@RequiresConfirmation(summary = "...")` to four methods on capability sub-interfaces. The annotation lives at the SPI level so every implementation — TrueLayer, Google, ref, future providers — inherits confirmation automatically. No provider can skip it.

| Module | Interface | Method | Summary Template |
|--------|-----------|--------|------------------|
| `bank-spi` | `PaymentInitiation` | `initiatePayment(PaymentRequest payment)` | `"Initiate payment of ${payment.amount} ${payment.currency} to ${payment.beneficiaryName}"` |
| `commerce-spi` | `Checkout` | `checkout(CheckoutRequest request)` | `"Checkout cart ${request.cartId}"` |
| `document-spi` | `FileOperations` | `delete(String fileId)` | `"Delete file ${fileId}"` |
| `contacts-spi` | `ContactWrite` | `delete(String contactId)` | `"Delete contact ${contactId}"` |

Templates use `${paramName.fieldName}` syntax resolved by `SummaryTemplateParser` at runtime (single-level bean-property access via record accessors).

### Dependency Changes

Each of the four SPI modules gains one new dependency:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-api</artifactId>
</dependency>
```

Version is managed by the parent POM — no version tag needed. These modules currently depend only on `casehub-platform-simulation-api`.

### Decorator Generation

No code generation happens in the SPI modules. The `ConfirmationDecoratorProcessor` (a Quarkus build extension in `platform-api`) discovers annotated methods via Jandex at application build time and generates CDI `@Decorator` classes in the consuming app. The decorator calls `ConfirmationInterceptorCore.requireConfirmation(actorId, tenancyId, descriptor)` which delegates to `OperationConfirmationProvider.confirm()`. If the result is not `CONFIRMED`, a `ConfirmationRequiredException` is thrown — the operation never executes.

The SPI modules just carry annotation metadata. The runtime machinery is entirely platform-owned.

### Dev/Test Behaviour

In dev/test environments, behaviour depends on the `OperationConfirmationProvider` CDI bean:
- **No provider registered:** decorator is not activated (CDI unsatisfied dependency)
- **Always-confirm provider:** operations proceed without human interaction
- **Mock provider:** tests can assert confirmation was requested without external systems

Ref implementations (`bank-ref`, `commerce-ref`, `document-ref`, `contacts-ref`) require no code changes.

### Testing Strategy

1. **Annotation presence tests** — one test per SPI module verifying the annotation exists on the correct method with the expected summary template. Reflection-based, no CDI container needed.
2. **Build verification** — `mvn clean install` confirms the processor discovers annotated methods and generates decorator classes without errors.

No end-to-end confirmation flow tests in this repo — that's platform infrastructure tested in the platform repo.

### Scope Boundaries

**In scope:**
- Add `casehub-platform-api` dependency to four SPI modules
- Add `@RequiresConfirmation` annotation to four methods
- Write annotation-presence tests for each annotated method

**Out of scope:**
- Confirmation delivery channels (already handled by existing connectors)
- `OperationConfirmationProvider` implementations (platform responsibility)
- Changes to ref implementations or existing provider implementations
- Changes to TrueLayerConsentService (PSD2 consent is connection-level, not operation-level)

## References

- platform#561 — OperationConfirmation SPI (closed, shipped)
- platform#525 — Identity Authentication epic
- `@RequiresConfirmation` annotation in `io.casehub.platform.api.confirmation`
- `ConfirmationDecoratorProcessor` — Quarkus build extension generating CDI decorators
- `SummaryTemplateParser` — resolves `${param.field}` templates
- `@SimulationEligible` — precedent for annotation on SPI interfaces
- TrueLayerConsentService — complementary connection-level consent (not replaced by this work)
