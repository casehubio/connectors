package io.casehub.connectors.contacts.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ContactsPlatformService {

    private final Map<String, ContactsPlatform> platforms;

    public ContactsPlatformService(List<ContactsPlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(ContactsPlatform::id, p -> p));
    }

    public ContactsPlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No contacts platform: " + id);
        }
        return platform;
    }

    public boolean supports(String id) {
        return platforms.containsKey(id);
    }

    public Set<String> ids() {
        return platforms.keySet();
    }
}
