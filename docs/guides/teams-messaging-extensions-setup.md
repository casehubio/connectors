# Teams Messaging Extensions Setup Guide

Deploy messaging extensions (search, action, link unfurling) to a real
Teams tenant using the casehub-connectors InvokeHandler SPI.

## Prerequisites

Complete these steps in the Azure Portal and Teams Developer Portal
before configuring your application:

- [ ] **Azure Bot registration** — create an Azure Bot resource or Bot
  Channel Registration with a messaging endpoint pointing to your
  application's webhook URL (`https://<host>/connectors/teams-inbound/webhook`).
  [Create an Azure Bot](https://learn.microsoft.com/en-us/azure/bot-service/bot-service-quickstart-registration)

- [ ] **Teams channel enabled** — enable the Microsoft Teams channel on
  the bot registration.
  [Connect a bot to Teams](https://learn.microsoft.com/en-us/azure/bot-service/channel-connect-teams)

- [ ] **App ID and password** — note the bot's app ID (also called
  Microsoft App ID) and generate a client secret. The app ID goes into
  Quarkus config; the secret is resolved via `ServiceConnectionProvider`.
  [Manage bot credentials](https://learn.microsoft.com/en-us/azure/bot-service/bot-service-manage-overview)

- [ ] **Teams app package** — create a ZIP containing your manifest,
  plus two icon files (color.png 192x192, outline.png 32x32). Upload
  via Teams Admin Center or sideload for development.
  [Upload a custom app](https://learn.microsoft.com/en-us/microsoftteams/platform/concepts/deploy-and-publish/apps-upload)

## Manifest Template

A ready-to-customise manifest template is provided at:

    chat-teams/src/main/resources/manifest-template.json

Copy this file and replace all `{{PLACEHOLDER}}` values with your
application's details. The template includes:

- A **search command** (`searchItems`) for `composeExtension/query`
- An **action command** (`createItem`) for `composeExtension/fetchTask`
  and `composeExtension/submitAction`
- A **link unfurling handler** via `messageHandlers` for
  `composeExtension/queryLink`

Remove any sections you don't need. Add additional commands by
duplicating entries in the `commands` array — each command needs a
unique `id`.

### Manifest schema version

The template uses schema version **1.13**, the minimum required for all
messaging extension features. Use 1.13 or higher.

## InvokeHandler Wiring

Messaging extension activities arrive as Bot Framework `invoke`
activities. The `TeamsInboundConnector` routes them through
`InvokeService`, which dispatches to `InvokeHandler` CDI beans by
matching `InvokeHandler.name()` to the invoke activity name.

### Activity name mapping

| Manifest element | Invoke activity name | InvokeHandler.name() |
|---|---|---|
| Search command | `composeExtension/query` | `"composeExtension/query"` |
| Search result selected | `composeExtension/selectItem` | `"composeExtension/selectItem"` |
| Action command (fetch form) | `composeExtension/fetchTask` | `"composeExtension/fetchTask"` |
| Action command (submit) | `composeExtension/submitAction` | `"composeExtension/submitAction"` |
| Link unfurling | `composeExtension/queryLink` | `"composeExtension/queryLink"` |
| Settings URL | `composeExtension/querySettingUrl` | `"composeExtension/querySettingUrl"` |
| Settings saved | `composeExtension/setting` | `"composeExtension/setting"` |

### Implementing a handler

Create a CDI bean implementing `InvokeHandler`:

```java
import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MySearchHandler implements InvokeHandler {

    @Override
    public String name() {
        return "composeExtension/query";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        String queryText = request.value().getString("queryText", "");

        String attachmentJson = """
            {
              "contentType": "application/vnd.microsoft.card.adaptive",
              "content": {
                "type": "AdaptiveCard",
                "body": [{"type": "TextBlock", "text": "Result for: %s"}]
              },
              "preview": {
                "contentType": "application/vnd.microsoft.card.thumbnail",
                "content": {"title": "Result"}
              }
            }
            """.formatted(queryText);

        return new InvokeResponse.ComposeResult("list",
            java.util.List.of(attachmentJson));
    }
}
```

### Response variants

| Variant | Use case | Key fields |
|---|---|---|
| `ComposeResult(attachmentLayout, attachmentsJson)` | Search results, link unfurling cards, selectItem, submitAction results | `attachmentLayout`: `"list"` or `"grid"`. Each attachment: JSON with `contentType`, `content`, optional `preview` |
| `ComposeAuth(suggestedActionsJson)` | SSO/OAuth auth flow initiation | JSON with OAuth URL and action configuration |
| `ComposeContinue(title, cardJson)` | Task module forms for fetchTask and submitAction | `title`: dialog title. `cardJson`: Adaptive Card JSON |
| `Accepted()` | Acknowledgment (e.g. for `composeExtension/setting`) | No fields |
| `Message(text)` | Plain text reply | `text`: message string |

## commandId Routing

When a manifest declares **multiple commands of the same type** (e.g.
two search commands), the invoke activity name is the same for both
(`composeExtension/query`). The `commandId` field inside
`InvokeRequest.value()` distinguishes them.

The `commandId` values must match the `id` field of each command in the
manifest's `composeExtensions[].commands[]` array.

### Worked example

Manifest with two search commands:

```json
{
  "commands": [
    {
      "id": "searchProducts",
      "type": "query",
      "title": "Search products",
      "parameters": [{"name": "query", "title": "Query", "inputType": "text"}]
    },
    {
      "id": "searchOrders",
      "type": "query",
      "title": "Search orders",
      "parameters": [{"name": "query", "title": "Query", "inputType": "text"}]
    }
  ]
}
```

Handler routing by `commandId`:

```java
@ApplicationScoped
public class MultiSearchHandler implements InvokeHandler {

    @Override
    public String name() {
        return "composeExtension/query";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        String commandId = request.value().getString("commandId", "");
        return switch (commandId) {
            case "searchProducts" -> searchProducts(request);
            case "searchOrders"   -> searchOrders(request);
            default -> new InvokeResponse.Accepted();
        };
    }

    private InvokeResponse searchProducts(InvokeRequest request) {
        // Product search logic — return ComposeResult with product cards
        return new InvokeResponse.ComposeResult("list",
            java.util.List.of(/* attachment JSON strings */));
    }

    private InvokeResponse searchOrders(InvokeRequest request) {
        // Order search logic — return ComposeResult with order cards
        return new InvokeResponse.ComposeResult("list",
            java.util.List.of(/* attachment JSON strings */));
    }
}
```

**Single-command shortcut:** if a handler serves only one command,
`commandId` routing is unnecessary — process the request directly.

## Quarkus Configuration

| Property | Purpose | Default |
|---|---|---|
| `casehub.connectors.teams-bot.app-id` | Bot Framework app ID (from Azure registration) | _(empty)_ |
| `casehub.connectors.teams-bot.dev-mode` | Skip JWT validation in development | `false` |
| `casehub.connectors.teams-bot.service-url-allowlist` | Allowed Bot Framework service URLs | `*.botframework.com,smba.trafficmanager.net` |
| `casehub.connectors.teams-bot.openid-metadata-url` | OpenID metadata URL for JWT validation | `https://login.botframework.com/v1/.well-known/openidconfiguration` |
| `casehub.connectors.teams-bot.issuer` | Expected JWT issuer | `https://api.botframework.com` |

The bot password/client secret is resolved via `ServiceConnectionProvider`,
not a config property. Configure it through your application's credential
management system.

### Minimal application.properties

```properties
casehub.connectors.teams-bot.app-id=${TEAMS_BOT_APP_ID}
```

For development with sideloaded apps, enable dev mode to skip JWT
validation:

```properties
casehub.connectors.teams-bot.dev-mode=true
```

**Warning:** Never enable dev-mode in production — it disables all
authentication on the Teams webhook endpoint.
