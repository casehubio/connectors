package io.casehub.connectors.graphql;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifest.FieldDeclaration;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CredentialFormService {

    private final StaticCredentialStore credentialStore;
    private final ProviderCredentialFacade credentialFacade;
    private final ConnectorRegistry registry;
    private final Map<String, FormSession> sessions = new ConcurrentHashMap<>();

    record FormSession(String connectorId, ConnectorManifest manifest, boolean consumed) {}

    @Inject
    public CredentialFormService(StaticCredentialStore credentialStore,
                                 Instance<ProviderCredentialFacade> credentialFacadeInstance,
                                 Instance<ConnectorRegistry> registryInstance) {
        this.credentialStore = credentialStore;
        this.credentialFacade = credentialFacadeInstance.isResolvable()
                ? credentialFacadeInstance.get() : null;
        this.registry = registryInstance.isResolvable()
                ? registryInstance.get() : null;
    }

    CredentialFormService(StaticCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
        this.credentialFacade = null;
        this.registry = null;
    }

    CredentialFormService(StaticCredentialStore credentialStore,
                          ProviderCredentialFacade credentialFacade,
                          ConnectorRegistry registry) {
        this.credentialStore = credentialStore;
        this.credentialFacade = credentialFacade;
        this.registry = registry;
    }

    public String createSession(String connectorId, ConnectorManifest manifest) {
        var token = UUID.randomUUID().toString();
        sessions.put(token, new FormSession(connectorId, manifest, false));
        return token;
    }

    public Optional<FormSession> session(String token) {
        return Optional.ofNullable(sessions.get(token));
    }

    public String generateFormHtml(String token) {
        var session = sessions.get(token);
        if (session == null) return errorPage("Invalid or expired form link.");
        if (session.consumed()) return errorPage("This form has already been submitted.");

        var decl = session.manifest().connector();
        var fields = decl.auth() != null ? decl.auth().fields() : List.<FieldDeclaration>of();

        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>Configure ").append(esc(decl.id())).append("</title>");
        sb.append("<style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;padding:2rem}");
        sb.append(".card{max-width:480px;margin:0 auto;background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.12);padding:2rem}");
        sb.append("h1{font-size:1.25rem;margin-bottom:.25rem}");
        sb.append(".desc{color:#666;margin-bottom:1.5rem;font-size:.9rem}");
        sb.append("label{display:block;font-weight:600;margin-bottom:.25rem;font-size:.9rem}");
        sb.append(".help{color:#666;font-size:.8rem;margin-bottom:.25rem}");
        sb.append(".optional{color:#999;font-weight:400}");
        sb.append("input{width:100%;padding:.5rem;border:1px solid #ccc;border-radius:4px;font-size:.9rem;margin-bottom:1rem}");
        sb.append("input:focus{outline:none;border-color:#2563eb;box-shadow:0 0 0 2px rgba(37,99,235,.2)}");
        sb.append("button{width:100%;padding:.6rem;background:#2563eb;color:#fff;border:none;border-radius:4px;font-size:1rem;cursor:pointer}");
        sb.append("button:hover{background:#1d4ed8}");
        sb.append("</style></head><body>");
        sb.append("<div class=\"card\">");
        sb.append("<h1>Configure ").append(esc(decl.id())).append("</h1>");
        sb.append("<p class=\"desc\">").append(esc(decl.description())).append("</p>");
        sb.append("<form method=\"POST\">");

        for (var field : fields) {
            sb.append("<label>").append(esc(field.description()));
            if (!field.isRequired()) sb.append(" <span class=\"optional\">(optional)</span>");
            sb.append("</label>");
            if (field.help() != null) sb.append("<p class=\"help\">").append(esc(field.help())).append("</p>");

            var inputType = field.isSensitive() ? "password" : "text";
            sb.append("<input type=\"").append(inputType).append("\" name=\"").append(esc(field.name())).append("\"");
            if (field.example() != null) sb.append(" placeholder=\"").append(esc(field.example())).append("\"");
            if (field.defaultValue() != null) sb.append(" value=\"").append(esc(field.defaultValue())).append("\"");
            if (field.isRequired()) sb.append(" required");
            sb.append(">");
        }

        sb.append("<button type=\"submit\">Save credentials</button>");
        sb.append("</form></div></body></html>");
        return sb.toString();
    }

    public boolean submitCredentials(String token, Map<String, String> values) {
        var session = sessions.get(token);
        if (session == null || session.consumed()) return false;

        sessions.put(token, new FormSession(session.connectorId(), session.manifest(), true));

        var decl = session.manifest().connector();
        var credentialKey = decl.credentialKey();
        var authType = decl.auth() != null ? decl.auth().type() : "api-key";
        var credentialType = mapCredentialType(authType);
        var now = Instant.now();

        for (var entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            credentialStore.store(new StaticCredentialRecord(
                    "system", "default", credentialKey + "." + entry.getKey(),
                    entry.getValue(), credentialType, now, now));
            if (credentialFacade != null) {
                credentialFacade.store(credentialKey, entry.getKey(), entry.getValue());
            }
        }

        if (registry != null) {
            registry.updateStatusByCredentialKey(credentialKey);
        }
        return true;
    }

    public String successPage(String connectorId) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
             + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
             + "<title>Configured</title>"
             + "<style>*{box-sizing:border-box;margin:0;padding:0}"
             + "body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;padding:2rem}"
             + ".card{max-width:480px;margin:0 auto;background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.12);padding:2rem;text-align:center}"
             + "h1{font-size:1.25rem;color:#16a34a;margin-bottom:.5rem}"
             + "p{color:#666;font-size:.9rem}</style></head><body>"
             + "<div class=\"card\"><h1>Credentials saved</h1>"
             + "<p>" + esc(connectorId) + " is now configured. You can close this page.</p>"
             + "</div></body></html>";
    }

    private static String errorPage(String message) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
             + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
             + "<title>Error</title>"
             + "<style>*{box-sizing:border-box;margin:0;padding:0}"
             + "body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;padding:2rem}"
             + ".card{max-width:480px;margin:0 auto;background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.12);padding:2rem;text-align:center}"
             + "h1{font-size:1.25rem;color:#dc2626;margin-bottom:.5rem}"
             + "p{color:#666;font-size:.9rem}</style></head><body>"
             + "<div class=\"card\"><h1>Error</h1>"
             + "<p>" + esc(message) + "</p></div></body></html>";
    }

    private static CredentialType mapCredentialType(String authType) {
        return switch (authType) {
            case "bot-token" -> CredentialType.BOT_TOKEN;
            case "personal-access-token" -> CredentialType.PERSONAL_ACCESS_TOKEN;
            default -> CredentialType.API_KEY;
        };
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
