# Connector Gap Analysis: casehub vs n8n/Zapier — Integration Strategy

## Purpose

Data-driven reference for prioritising new connector integrations. Cross-references n8n (developer audience) and Zapier (business audience) integration catalogs with casehub's existing coverage, with recommendations on execution approach per integration.

## Platform Scale Context

| Metric | Zapier | n8n | casehub |
|--------|--------|-----|---------|
| Revenue | ~$310M ARR | ~$100M ARR | — |
| Valuation | $5B (2021) | $5.2B (May 2026) | — |
| Users | 3.4M businesses | 1.7M builders | — |
| Integrations | 10,500+ apps | ~400 built-in | 19 services, 16 SPIs |
| Triggers & actions | 66,000+ | — | — |
| Profitable | Since 2014 | Unknown | — |

### Why both catalogs matter

n8n skews developer/technical — strong on databases, APIs, DevOps. Zapier skews business/enterprise — strong on CRM, marketing, forms, accounting, HR. Using only n8n would miss entire categories that drive automation revenue.

## Integration Model

### Four tiers, priority order

| Tier | Executor | When to use | Auth model |
|------|----------|-------------|------------|
| **1. Rich Platform SPI** | Hand-written Java | Domain richness matters — cross-provider abstraction, capability introspection, `@SimulationEligible`, CDI events | CredentialResolver SPI, per-user |
| **2. OpenAPI Dynamic Step** | HttpHelper + OpenAPI spec | REST/HTTP SaaS APIs — bulk of the n8n gap. Spec-driven validation, curated noun/verb step types | Bearer/API key/OAuth2 from CredentialResolver |
| **3. Quarkus/Spring Native** | Framework extensions | Protocols with first-class Quarkus support — Kafka, Redis, MongoDB, MQTT, JDBC, Elasticsearch, gRPC | Framework-managed, bridged to CredentialResolver |
| **4. Direct Client Library** | Thin wrapper over protocol library | Non-HTTP protocols with no Quarkus support — FTP (Commons Net), SSH (MINA SSHD), LDAP (JNDI) | CredentialResolver → library config |

All dynamic steps share the same YAML surface. The user writes `ftp_upload(host, path, file)` or `notion_search(query)` and never knows what's underneath.

### Why not Apache Camel?

Camel's core value is routing, EIP orchestration, and data transformation between components. casehub's playbook executor already provides all three. Adding Camel would be a framework inside a framework — competing routing, competing error handling, competing lifecycle management.

For specific integrations:
- **REST APIs** — OpenAPI + HttpHelper is cleaner and more current than Camel wrappers
- **Kafka, Redis, MongoDB, MQTT, JDBC, Elasticsearch** — Quarkus has native extensions, superior to Camel's wrappers
- **FTP, SSH, LDAP** — thin wrapper over the underlying library (Commons Net, MINA SSHD, JNDI) is simpler than Camel + camel-quarkus dependency chain

Camel remains a reference for identifying *which* integrations matter (its component catalog is a useful signal), but not the execution path.

## Cross-Reference: All Integrations

### Legend

- **n8n Deploys** — workflow deployments from n8n Pulse (higher = more popular)
- **casehub** — existing casehub SPI or connector
- **Approach** — recommended execution strategy
- **Phase** — implementation priority (1 = highest)
- **Ops** — also needed by casehub/ops repo

### Already Covered by casehub (no action needed)

| Integration | n8n Deploys | casehub SPI |
|-------------|-------------|-------------|
| Slack | 125.1K | chat-slack |
| Discord | ~0 (trigger) | chat-discord |
| Signal | — | chat-signal |
| Google Calendar | 77.6K | calendar-google |
| Google Drive | 614.1K | document-google |
| Gmail | 562.5K | email-google |
| Google Contacts | — | contacts-google |
| Google Maps/Places | — | location-google |
| GitHub | 55.5K | project-github |
| Microsoft Teams | ~0 | teams connector |
| Twilio (SMS/Voice) | ~0 | mobile-twilio |
| WhatsApp | 331.9K | mobile-twilio |
| Shopify | 5.0K | commerce-shopify |
| Amazon (product) | — | commerce-amazon |
| TrueLayer (banking) | — | bank-truelayer |
| Amadeus (travel) | — | travel-amadeus |
| IRC | — | chat-irc |
| Dial (voice) | — | mobile-dial |

**Total: 19 external services across 16 SPIs**

### Quarkus/Spring Native — Protocol Connectors

These integrations have first-class Quarkus extensions. Prefer native over Camel or custom wrappers.

| Integration | n8n Deploys | Quarkus Extension | Ops? | Phase | Notes |
|-------------|-------------|-------------------|------|-------|-------|
| **PostgreSQL** | 71.3K | `quarkus-jdbc-postgresql` + Agroal | — | 1 | Connection pooling, Panache |
| **MySQL** | (with PG) | `quarkus-jdbc-mysql` + Agroal | — | 1 | Same JDBC pattern |
| **MongoDB** | ~60K | `quarkus-mongodb-client` | — | 1 | Reactive + imperative |
| **Redis** | 67.1K | `quarkus-redis` | — | 1 | Commands, pub/sub |
| **Kafka** | ~5K | `smallrye-reactive-messaging-kafka` | Yes | 1 | Consumer groups, exactly-once |
| **MQTT** | — | `smallrye-reactive-messaging-mqtt` | Yes | 2 | QoS, persistent sessions |
| **RabbitMQ** | ~5K | `smallrye-reactive-messaging-amqp` | — | 2 | AMQP 1.0 |
| **Elasticsearch** | ~5K | `quarkus-elasticsearch-rest-client` | — | 2 | REST client, bulk ops |
| **gRPC** | — | `quarkus-grpc` | Yes | 2 | Service definitions |
| **Kubernetes** | — | `quarkus-kubernetes-client` (Fabric8) | Yes | 2 | Watch, exec, API |
| **AWS S3** | ~40K | AWS SDK v2 (direct) | Yes | 1 | Quarkus has `quarkus-amazon-s3` |
| **AWS SQS** | — | AWS SDK v2 (direct) | Yes | 2 | `quarkus-amazon-sqs` |
| **AWS SNS** | — | AWS SDK v2 (direct) | Yes | 2 | `quarkus-amazon-sns` |
| **AWS Lambda** | — | AWS SDK v2 (direct) | Yes | 2 | `quarkus-amazon-lambda` |
| **AWS DynamoDB** | — | AWS SDK v2 (direct) | Yes | 2 | `quarkus-amazon-dynamodb` |
| **Google Cloud Storage** | — | Google Cloud SDK | — | 2 | Direct client |
| **Google BigQuery** | — | Google Cloud SDK | — | 2 | Direct client |
| **Google Pub/Sub** | — | Google Cloud SDK | — | 2 | Direct client |

### Direct Client Library — Non-HTTP Protocol Connectors

Protocols with no Quarkus extension. Thin wrapper over the underlying library.

| Integration | n8n Deploys | Library | Ops? | Phase | Notes |
|-------------|-------------|---------|------|-------|-------|
| **FTP** | 7.2K | Apache Commons Net | — | 2 | Active/passive, binary/ascii, resume |
| **SFTP** | (with FTP) | JSch / Apache MINA SSHD | — | 2 | SSH key auth, known hosts |
| **SSH** | 14.2K | Apache MINA SSHD | Yes | 2 | Remote command execution |
| **LDAP** | — | Java JNDI (built-in) | — | 3 | Directory services |
| **SMPP** | — | jSMPP | — | 3 | SMS gateway protocol |
| **Docker** | — | docker-java | Yes | 3 | Docker Engine API |

### OpenAPI Dynamic Steps — REST SaaS APIs

The bulk of the n8n gap. All are modern SaaS with REST APIs.

| Integration | n8n Deploys | Auth Type | Phase | Notes |
|-------------|-------------|-----------|-------|-------|
| **Google Sheets** | 1.9M | OAuth2 (existing GoogleCredentialResolver) | 1 | #1 n8n integration by 2x |
| **Telegram** | 951.6K | Bot token (bearer) | 1 | #2 n8n integration; rich enough for ChatPlatform SPI (chat-telegram) via `telegrambots` SDK, not a dynamic step |
| **Airtable** | 161.9K | Bearer token (API key) | 1 | Low-code database |
| **Facebook/Meta Graph** | 145.0K | OAuth2 | 2 | Social media automation |
| **Google Docs** | 98.8K | OAuth2 (existing GoogleCredentialResolver) | 1 | Completes Google Workspace coverage |
| **Microsoft Outlook** | 96.3K | OAuth2 (Graph API) | 1 | One Graph API client covers Outlook + OneDrive + Excel |
| **Microsoft OneDrive** | 12.7K | OAuth2 (Graph API) | 1 | Covered by Graph API with Outlook |
| **Microsoft Excel 365** | high | OAuth2 (Graph API) | 1 | Covered by Graph API with Outlook |
| **Notion** | 87.7K | Bearer token (API key) | 1 | Team workspace |
| **OpenAI** | 839.5K | API key | 2 | AI — consider richer SPI long-term |
| **Supabase** | 56.0K | API key + JWT | 2 | PostgreSQL-backed BaaS |
| **WordPress** | 43.0K | Various (API key, OAuth2, JWT) | 3 | CMS REST API |
| **Jira** | 25.0K | OAuth2 / API token | 2 | Atlassian ecosystem; publishes OpenAPI spec |
| **HubSpot** | 22.1K | OAuth2; OpenAPI published | 2 | CRM |
| **SendGrid** | ~15K | API key | 2 | Transactional email |
| **Reddit** | 15.2K | OAuth2 | 3 | Social media |
| **Salesforce** | 10.4K | OAuth2 (JWT bearer) | 2 | High enterprise value |
| **Todoist** | 9.8K | Bearer token | 3 | Task management |
| **Dropbox** | 8.0K | OAuth2 | 3 | Cloud storage |
| **Pipedrive** | 6.7K | API key / OAuth2 | 3 | CRM |
| **WooCommerce** | 5.7K | OAuth1/API key | 3 | E-commerce |
| **Twitter/X** | 71.0K | OAuth2 | 3 | Social media |
| **ClickUp** | 4.3K | API key / OAuth2 | 3 | Project management |
| **Zendesk** | 3.9K | OAuth2 / API token | 3 | Support/ticketing |
| **Trello** | 3.4K | API key + token | 3 | Kanban boards |
| **Monday.com** | 3.3K | API key (GraphQL) | 3 | Project management |
| **GitLab** | 3.3K | OAuth2; OpenAPI published | 3 | DevOps |
| **Stripe** | 1.8K (trigger) | API key (bearer) | 2 | Excellent OpenAPI spec |
| **Asana** | ~8K | OAuth2; OpenAPI published | 3 | Project management |
| **Linear** | community | API key (GraphQL + REST) | 3 | Dev project management |
| **Mailchimp** | — | OAuth2 / API key | 3 | Email marketing |
| **Zoom** | — | OAuth2 | 3 | Video conferencing |
| **PayPal** | — | OAuth2 | 3 | Payments |
| **Webflow** | — | OAuth2 / API key | 3 | CMS/website builder |
| **Zoho CRM** | 2.2K | OAuth2 | 3 | CRM |
| **QuickBooks Online** | — | OAuth2 | 3 | Accounting |
| **Xero** | — | OAuth2 | 3 | Accounting |
| **Calendly** | ~0 | OAuth2 / API key | 3 | Scheduling |
| **ServiceNow** | ~5K | OAuth2 / Basic | 3 | Enterprise ITSM |
| **Box** | — | OAuth2 | 3 | Cloud storage |
| **Braintree** | — | API key | 3 | Payments |
| **OpenWeatherMap** | 5.0K | API key | 3 | Utility |

### Enterprise/Industrial Differentiation (n8n doesn't offer)

Niche protocols where casehub could leapfrog n8n. Evaluate on demand — not gap-closing, but differentiation.

| Integration | Library | Domain | Notes |
|-------------|---------|--------|-------|
| SAP NetWeaver | SAP JCo / Camel if needed | Enterprise | ERP integration |
| FHIR (HL7) | HAPI FHIR | Healthcare | Maps to health-spi |
| MLLP (HL7v2) | Custom / Camel if needed | Healthcare | Medical messaging |
| FIX (QuickFIX/J) | QuickFIX/J | Finance | Trading protocol |
| AS2 | Mendelson AS2 | B2B | EDI file transfer |
| PLC4X | PLC4X | Industrial IoT | PLC device integration |
| OPC UA | Eclipse Milo | Industrial IoT | Telemetry |

## POC Plan — Prove the Patterns

Four POC integrations, each exercising a distinct integration axis. After these, every future integration is a subtype of a proven pattern.

| POC | Tier | Auth Pattern | What it proves | Subtype covers |
|-----|------|-------------|----------------|----------------|
| **Notion** | OpenAPI | API key (bearer) | Simplest OpenAPI path — CRUD, pagination | Airtable, Linear, SendGrid, Todoist, any API-key SaaS |
| **Google Sheets** | OpenAPI | OAuth2 per-user (existing resolver) | OAuth2 reuse, pagination, batch ops | Google Docs, BigQuery, any Google API |
| **Microsoft Graph** | OpenAPI | OAuth2 multi-scope | One spec → many step types (Outlook + OneDrive + Excel) | Any Microsoft 365 service, multi-domain APIs |
| **FTP** | Direct library | Connection credentials | Non-HTTP protocol, thin wrapper, proves Camel unnecessary | SFTP, SSH, LDAP — any protocol needing a client library |

### What the POCs validate end-to-end

- Dynamic step registration from metadata (no compiled `@Plugin`)
- YAML step authoring experience (noun/verb, typed parameters)
- Credential resolution through existing SPI
- Error mapping to `ProviderApiException` hierarchy
- Coexistence with compiled `@Plugin` steps
- Operation curation from large API surfaces

## Platform SPI Mapping — The Differentiator

Zapier has 10,500 apps but treats every integration as a flat connector. casehub abstracts across providers at the domain level. One playbook works against any provider in the domain.

### Existing SPIs — Add New Providers

These integrations map directly to SPIs already built. No new SPI design, no new MCP tools, no new step types. Just implement the interface and wire the SDK.

| Integration | n8n Deploys | Zapier Rank | Existing SPI | Provider module | SDK/API |
|---|---|---|---|---|---|
| Telegram | 951.6K | — | ChatPlatform | chat-telegram | `telegrambots` |
| Jira | 25.0K | — | ProjectPlatform | project-jira | Atlassian REST |
| GitLab | 3.3K | — | ProjectPlatform | project-gitlab | GitLab4J |
| Linear | community | — | ProjectPlatform | project-linear | REST API |
| Asana | ~8K | — | ProjectPlatform | project-asana | `asana-java` |
| Trello | 3.4K | #17 Zapier | ProjectPlatform | project-trello | REST API |
| ClickUp | 4.3K | — | ProjectPlatform | project-clickup | REST API |
| Monday.com | 3.3K | #22 Zapier | ProjectPlatform | project-monday | GraphQL |
| Todoist | 9.8K | — | ProjectPlatform | project-todoist | REST API |
| Dropbox | 8.0K | — | DocumentPlatform | document-dropbox | `dropbox-sdk-java` |
| Box | — | — | DocumentPlatform | document-box | `box-java-sdk` |
| OneDrive | 12.7K | — | DocumentPlatform | document-microsoft | `microsoft-graph` |
| SharePoint | — | #11 premium | DocumentPlatform | document-sharepoint | `microsoft-graph` |
| Outlook (email) | 96.3K | #13 Zapier | EmailPlatform | email-microsoft | `microsoft-graph` |
| Outlook (calendar) | — | — | CalendarPlatform | calendar-microsoft | `microsoft-graph` |
| WooCommerce | 5.7K | — | CommercePlatform | commerce-woocommerce | REST API |

**16 integrations, zero new SPI design.** Microsoft Graph SDK alone covers 4 of them (email, calendar, documents, SharePoint).

### New Platform SPIs — Categories to Create

Surfaced by cross-referencing Zapier (business categories) with n8n (developer categories).

| Proposed SPI | Zapier Signal | n8n Signal | First Provider | Capability Sub-interfaces | Notes |
|---|---|---|---|---|---|
| **CrmPlatform** | 2,048 apps; Salesforce #18 overall | 10.4K (SF) | Salesforce or HubSpot | Contacts, Companies, Deals, Pipelines, Activities | Overlaps ContactsPlatform for contacts; CRM adds deal/pipeline layer |
| **PaymentPlatform** | Stripe #11 overall; PayPal #13 premium | 1.8K (Stripe) | Stripe | Charges, Customers, Subscriptions, Invoices, Refunds | Distinct from BankPlatform (merchant-side vs bank-side) |
| **FormsPlatform** | Google Forms #8, Typeform #15, Jotform #20 | Low | Google Forms or Typeform | Forms, Submissions, Fields, Webhooks | Primary automation trigger on Zapier; inbound-focused |
| **SpreadsheetPlatform** | Google Sheets #1 overall | 1.9M | Google Sheets | Sheets, Rows, Columns, Ranges, Queries | Airtable, Notion DB are variants; scope to "structured tabular data" |
| **SchedulingPlatform** | Calendly #14 overall | ~0 | Calendly | Availability, Bookings, EventTypes | Different from CalendarPlatform (scheduling slots vs calendar events) |
| **AccountingPlatform** | QuickBooks #4 premium, Xero #7 premium | — | Xero or QuickBooks | Invoices, Expenses, Accounts, Contacts, BankFeeds | Enterprise value; 2 major providers with very similar APIs |
| **SupportPlatform** | 223 apps; Zendesk #8 premium | 3.9K | Zendesk or Intercom | Tickets, Agents, Customers, SLAs, KnowledgeBase | Overlaps with CRM on customer records |
| **SocialPlatform** | Facebook #7 (Lead Ads) | 71K (X), 145K (FB) | Facebook/Meta or Twitter/X | Posts, Feeds, Comments, Reactions, Followers, AdLeads | Ads/Lead Gen is the high-value sub-domain |
| **MarketingPlatform** | Mailchimp #10 overall; 1,255 apps | — | Mailchimp or SendGrid | Campaigns, Audiences, Templates, Sends, Analytics | SendGrid (transactional) vs Mailchimp (campaigns) may be two sub-interfaces |
| **WeatherPlatform** | — | 5.0K | OpenWeatherMap | CurrentConditions, Forecast, Alerts, Historical | Multiple providers (OWM, AccuWeather, Tomorrow.io, Open-Meteo) |
| **HrPlatform** | BambooHR #20 premium; 315 apps | — | BambooHR | Employees, TimeOff, Directory, Onboarding | Enterprise category, barely exists in n8n |
| **MeetingsPlatform** | — | — | Microsoft Teams or Zoom | Meetings, Participants, Recordings, Transcripts, LLM Participation (see below) | Teams meetings via Graph SDK (#211), Zoom, Google Meet, Webex |
| **CmsPlatform** | WordPress in top 22; 263 apps | 43.0K | WordPress | Posts, Pages, Media, Categories, Tags | WordPress, Webflow, Ghost, Strapi |

### Coverage Heat Map — Before and After

**Before (today — 16 SPIs):**

| Category | Status | Providers |
|---|---|---|
| Chat | Green | Slack, Discord, Signal, IRC |
| Calendar | Yellow | Google only |
| Banking | Yellow | TrueLayer only |
| Email (read) | Yellow | Gmail only |
| Email (send) | Green | SMTP |
| Documents | Yellow | Google Drive only |
| Contacts | Yellow | Google only |
| Location | Yellow | Google only |
| Commerce | Green | Amazon, Shopify |
| Travel | Green | Amadeus, Google |
| Project mgmt | Yellow | GitHub only |
| Health | Yellow | Ref only |
| Voice | Yellow | Ref only |
| Mobile | Green | Twilio, Dial |
| CRM | **RED** | — |
| Payments | **RED** | — |
| Spreadsheets | **RED** | — |
| Forms | **RED** | — |
| Scheduling | **RED** | — |
| Accounting | **RED** | — |
| Support | **RED** | — |
| Social media | **RED** | — |
| Marketing | **RED** | — |
| Weather | **RED** | — |
| HR | **RED** | — |
| CMS | **RED** | — |
| Meetings/Video | **RED** | — |

**After Phase 1 — One provider per red category + second provider for yellows:**

All reds turn yellow (one provider each). All yellows turn green (two+ providers). An LLM scanning casehub sees coverage across the board.

## Phase Summary

### Phase 1 — Eliminate red areas, strengthen yellows

Goal: every category has at least one real provider. No empty domains.

**New providers for existing SPIs:**
- Telegram → ChatPlatform (chat-telegram)
- Outlook email → EmailPlatform (email-microsoft) — via Graph SDK
- Outlook calendar → CalendarPlatform (calendar-microsoft) — via Graph SDK
- OneDrive → DocumentPlatform (document-microsoft) — via Graph SDK
- Jira → ProjectPlatform (project-jira)

**New SPIs with first provider:**
- CrmPlatform → Salesforce or HubSpot
- PaymentPlatform → Stripe
- SpreadsheetPlatform → Google Sheets
- FormsPlatform → Google Forms or Typeform
- SchedulingPlatform → Calendly
- AccountingPlatform → Xero or QuickBooks
- SupportPlatform → Zendesk
- SocialPlatform → Facebook/Meta (Lead Ads is #7 on Zapier)

**Quarkus native:**
- JDBC (Postgres, MySQL)
- Redis
- Kafka (also ops)
- AWS S3 (also ops)

**Estimated coverage after Phase 1: ~40 services across ~28 SPIs** — no red areas

### Phase 2 — Depth and choice

Second providers for new SPIs, plus broader protocol coverage.

**Second providers:** HubSpot or Pipedrive (CRM), PayPal (Payments), Airtable (Spreadsheets), Typeform or Jotform (Forms), Notion (Spreadsheets or new?), Intercom (Support), Twitter/X (Social), Mailchimp (Marketing), Cal.com (Scheduling)

**More existing SPI providers:** Dropbox/Box (Documents), GitLab/Linear (Project), MongoDB, Elasticsearch

**Quarkus native:** MQTT, RabbitMQ, Kubernetes, AWS (SQS/SNS/Lambda/DynamoDB)

**OpenAPI/SDK:** OpenAI, SendGrid, Supabase

**Estimated coverage after Phase 2: ~65 services**

### Phase 3 — Long tail, verticals, differentiation

**Remaining providers:** Trello, Asana, Monday.com, ClickUp, Todoist (Project); Zoho, Pipedrive (CRM); Webflow, Ghost (CMS); LinkedIn, Reddit (Social); Zoom, Mailgun; QuickBooks/Xero second provider (Accounting)

**New SPIs on demand:** WeatherPlatform, HrPlatform, CmsPlatform

**Direct library:** FTP/SFTP, SSH, LDAP, SMPP, Docker

**Enterprise (on demand):** SAP, FHIR, FIX, PLC4X

**Estimated coverage after Phase 3: ~95+ services across ~30+ SPIs**

## MeetingsPlatform — LLM Participation Model

Beyond standard meeting management (create, join, record), MeetingsPlatform enables LLM agents to participate in meetings as active attendees. Real-time video understanding isn't practical, but meaningful participation is achievable through existing platform capabilities:

| Capability | What the LLM does | Builds on |
|---|---|---|
| **Join/Leave** | Connect to meeting via provider API | MeetingsPlatform core |
| **Listen** | STT on meeting audio stream | VoicePlatform (STT) |
| **Speak** | TTS output routed to meeting audio, via synthetic avatar | VoicePlatform (TTS) + avatar service (HeyGen, D-ID, Synthesia) |
| **Present** | Screen share with controlled content (slides, dashboards, docs) | Meeting API share controls |
| **Observe** | Periodic frame capture from video feed → vision model analysis | Screenshot + LLM vision |
| **Read** | Consume live meeting transcript | Provider-native transcription (Teams/Zoom built-in) |

The VoicePlatform and MobilePlatform work already provides the STT/TTS foundation. The meeting-specific additions are join/leave, share controls, and audio stream routing. Avatar rendering is an external service integration, not a platform capability.

## POC Plan — Prove the Patterns

Four POC integrations, each exercising a distinct integration axis. After these, every future integration is a subtype of a proven pattern.

| POC | Tier | Auth Pattern | What it proves | Subtype covers |
|-----|------|-------------|----------------|----------------|
| **Notion** | OpenAPI | API key (bearer) | Simplest OpenAPI path — CRUD, pagination | Airtable, Linear, SendGrid, Todoist, any API-key SaaS |
| **Google Sheets** | OpenAPI/SDK | OAuth2 per-user (existing resolver) | OAuth2 reuse, pagination, batch ops | Google Docs, BigQuery, any Google API |
| **Microsoft Graph** | OpenAPI/SDK | OAuth2 multi-scope | One spec → many step types (Outlook + OneDrive + Excel) | Any Microsoft 365 service, multi-domain APIs |
| **FTP** | Direct library | Connection credentials | Non-HTTP protocol, thin wrapper | SFTP, SSH, LDAP — any protocol needing a client library |

### What the POCs validate end-to-end

- Dynamic step registration from metadata (no compiled `@Plugin`)
- YAML step authoring experience (noun/verb, typed parameters)
- Credential resolution through existing SPI
- Error mapping to `ProviderApiException` hierarchy
- Data transformation between steps (JQ/expressions)
- Coexistence with compiled `@Plugin` steps
- Operation curation from large API surfaces

## Plugin Federation — The Long Tail Strategy

casehub doesn't need 10,500 integrations. It needs 30 SPIs and a federation mechanism. The SPI contract is the compatibility guarantee — anyone who implements it gets playbook support, MCP tools, simulation, and credential resolution for free.

### Two plugin tiers

| | Compiled (JAR) | Descriptor (YAML/JSON) |
|---|---|---|
| What it is | Full SPI implementation in Java | Metadata describing a REST API mapped to an SPI contract |
| Requires restart | Yes | **No — runtime addable** |
| What it can do | Custom logic, complex mapping, CDI events, webhook handling, OAuth2 flows, event streaming | Call REST APIs, map parameters, return results |
| Who writes it | Developers | Anyone — including LLMs |
| Discovery | Classpath plugin folder or Maven coordinate | Watched directory or API endpoint |
| Examples | chat-telegram, bank-truelayer, payment-stripe | Linear, Airtable, company-internal APIs |

### Compiled plugins (restart required)

For providers that need real logic — webhook handling, OAuth2 flows, event streaming, complex pagination, CDI event firing.

```yaml
# casehub config
plugins:
  compiled:
    - url: https://github.com/someone/casehub-plugin-linear/releases/latest/plugin.jar
    - url: https://internal.company.com/artifacts/crm-dynamics365-1.0.jar
    - url: file:///opt/casehub/plugins/custom-erp.jar
```

Plugin authors depend on SPI modules (`project-spi`, `crm-spi`, etc.) — nothing else. CDI discovers the beans at startup.

### Descriptor plugins (runtime, no restart)

For the 80% of integrations that are "call a REST API with credentials." A descriptor maps API operations to an SPI contract.

```yaml
plugin: linear
type: project    # maps to ProjectPlatform contract
api:
  base: https://api.linear.app
  auth: bearer
operations:
  create_issue:
    method: POST
    path: /graphql
    # parameter mapping...
  list_issues:
    method: POST
    path: /graphql
    # ...
```

Drop into a watched directory — executor picks it up, validates against the SPI contract, registers the provider. No compilation, no classloading, no restart.

### LLM-generated plugins

The descriptor format is simple enough for an LLM to generate. "Connect casehub to my company's internal API" becomes: LLM reads the OpenAPI spec, generates the descriptor YAML, drops it in the plugin directory. Runtime. No developer needed.

This is the long tail strategy: casehub maintains 30 SPIs and compiled providers for top-tier services. The descriptor ecosystem handles everything else — community-contributed, LLM-generated, or company-specific.

### Ecosystem roles

| Role | Responsibility | Boundary |
|---|---|---|
| **SPI author** (casehub team) | Define what a CRM, payment system, or chat platform *is* | Domain contract |
| **Plugin author** (anyone) | Map a specific service's API to the SPI contract | Provider implementation |
| **Playbook author** (end users) | Write automation logic using domain operations | Never thinks about providers |

Zapier and n8n collapse all three — their node authors must understand the framework, the API, and the user-facing experience. casehub's SPI split means a plugin author only needs the domain interface and the target API.

## Data Transformation — The Missing Step Type

Playbooks currently lack a built-in data transformation step. This is needed for mapping between step outputs and inputs — field renaming, format conversion, data extraction.

### The gap

- Step A outputs `{customer_name: "John", email: "john@example.com"}`
- Step B expects `{name: "John", contact_email: "john@example.com"}`
- No built-in way to bridge them without a compiled step

### Recommended approach

**JQ expressions** — the playbook executor theoretically supports any expression language. JQ is declarative, widely understood, and a single library (`jackson-jq`). The `jq-platform-evaluator` exists in the spec history.

A `transform` step type with JQ/expression support:

```yaml
- step: transform
  mapping:
    name: "{{ customer.full_name }}"
    contact_email: "{{ customer.email }}"
```

**Format parsers as step types** — `parse_csv`, `parse_xml`, `format_json`. Each wraps a standalone library (Jackson, OpenCSV). No framework needed.

### POC validation needed

The expression language support needs end-to-end testing. The POC should chain: OpenAPI step → transform → next step, proving the full pipeline in YAML with no compiled code.

## Key Design Decisions (from analysis)

| Decision | Rationale |
|---|---|
| **No Apache Camel** | casehub's playbook executor already provides routing, EIPs, and data transformation. Camel would be a framework inside a framework. Quarkus native extensions and direct libraries are simpler for every use case. |
| **OpenAPI/SDK over Camel for REST APIs** | Camel wraps REST APIs without adding value. Direct SDK or OpenAPI + HttpHelper is more current, simpler, and integrates with existing credential resolvers. |
| **Quarkus native over Camel for protocols** | Kafka, Redis, MongoDB, MQTT, JDBC, Elasticsearch all have Quarkus extensions that integrate natively with CDI — superior to Camel's wrappers. |
| **Platform SPIs over flat connectors** | casehub's differentiator. Zapier has 10,500 flat connectors; casehub has 30 SPIs with cross-provider abstraction, capability introspection, and simulation. "Zapier connects to apps. casehub understands them." |
| **Two plugin tiers** | Compiled JARs for complex providers (restart required). YAML descriptors for REST API providers (runtime, no restart). The descriptor tier enables community/LLM-generated plugins. |
| **Data transformation via JQ** | Not Camel's DataFormat system. Step model works with typed data structures, not byte streams. JQ is the right abstraction level. |
| **Ops unification** | AWS, k8s, Docker, Kafka client/credential layers live in connectors, consumed by ops. One place to talk to each external system. Investigation of ops repo needed before implementation. |

## Community/AI Trends

- **AI nodes dominate n8n growth:** OpenAI (1.7M), AI Agent (1.8M). Anthropic growing fastest (12% → 21% share in 6 months). Consider whether casehub needs an AI/LLM SPI or if MCP tools cover this.
- **WhatsApp community nodes** lead npm downloads (Evolution API: 6.6M/month). Signals strong demand outside Twilio's offering.
- **Data Table** (n8n's built-in database) growing 2.16x — absorbing Google Sheets and Postgres use cases for simple data storage.
- **Web scraping** nodes (Firecrawl, Apify, Puppeteer) are a significant community category. Different competitive axis.
- **Forms as triggers** — Zapier's data shows forms (Google Forms, Typeform, Jotform) are a top-5 automation trigger category. FormsPlatform is primarily inbound/trigger-focused.

## Cross-Repo Considerations

AWS, Kubernetes, Docker, MQTT, and Kafka integrations are also needed by `casehub/ops`. The client/credential layer should live in connectors and be consumed by ops — one place to talk to AWS, one place to talk to k8s. Investigation of ops' existing client code is needed before implementation to determine unification approach.

## Zapier's Top 22 Apps (by active Zaps)

For reference — Zapier's most popular integrations by business usage:

1. Google Sheets, 2. Gmail, 3. Slack, 4. Google Calendar, 5. Google Drive, 6. HubSpot, 7. Facebook Lead Ads, 8. Google Forms, 9. Notion, 10. Mailchimp, 11. Stripe, 12. LeadConnector, 13. Microsoft Outlook, 14. Calendly, 15. Typeform, 16. Airtable, 17. Trello, 18. Salesforce, 19. Google Docs, 20. Jotform, 21. Gravity Forms, 22. Monday.com

## Sources

- Zapier Apps Directory (zapier.com/apps)
- n8n Pulse — Node Usage Statistics (n8n-pulse.gui.do)
- Apache Camel 2025 Download Statistics (camel.apache.org/blog/2026/02/2025-downloads/)
- mvnrepository.com — Camel component rankings
- awesome-n8n GitHub — Top Community Nodes
- AiAdvisors — n8n AI Index (aiadvisors.pl)
- n8n Revenue & Growth — Sacra, Okara, flowlyn
- Enlyft — Apache Camel Market Share
