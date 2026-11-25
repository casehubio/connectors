package io.casehub.connectors.mobile.steps;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatformService;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.LinkedHashMap;
import java.util.regex.Pattern;

@Plugin(value = "wait-for-sms", description = "Poll for SMS matching a pattern — fails on timeout", absorbSiblings = true)
public record WaitForSmsPlugin(
        @Required String provider,
        @Required String to,
        @Required String pattern,
        @Optional String timeout) {

    private static final long POLL_INTERVAL_MS = 5000;
    private static final long DEFAULT_TIMEOUT_MS = 120_000;

    @Execute
    public Result execute(MobilePlatformService platformService) {
        var timeoutMs = parseTimeout(timeout != null ? timeout : "120s");
        var regex = Pattern.compile(pattern);
        var platform = platformService.platform(provider);
        if (!platform.supports(MobilePlatform.Sms.class)) {
            return Result.failed("Provider '" + provider + "' does not support SMS");
        }
        var sms = platform.sms();
        var deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            var page = sms.listMessages(PageRequest.first(100));
            for (var msg : page.items()) {
                if (!to.equals(msg.to())) continue;
                var matcher = regex.matcher(msg.body());
                if (matcher.find()) {
                    var output = new LinkedHashMap<String, Object>();
                    output.put("from", msg.from());
                    output.put("body", msg.body());
                    if (matcher.groupCount() > 0) {
                        output.put("code", matcher.group(1));
                    }
                    return Result.of(output);
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("Interrupted while waiting for SMS to " + to);
            }
        }
        return Result.failed("Timed out after " + (timeoutMs / 1000) + "s waiting for SMS matching '"
                + pattern + "' to " + to);
    }

    private static long parseTimeout(String value) {
        if (value.endsWith("s")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1000;
        }
        if (value.endsWith("m")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 60_000;
        }
        return DEFAULT_TIMEOUT_MS;
    }
}
