package io.casehub.connectors.graphql;

import io.casehub.connectors.location.ref.RefLocationPlatform;
import io.casehub.connectors.location.ref.InMemoryLocationBackend;
import io.casehub.connectors.location.spi.LocationPlatformService;
import io.casehub.connectors.mobile.ref.RefMobilePlatform;
import io.casehub.connectors.mobile.ref.InMemoryMobileBackend;
import io.casehub.connectors.mobile.spi.MobilePlatformService;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;

import java.util.List;

public final class PlaybookApiServices {
    public static final class Location extends ConnectorLocationApi {
        public Location() throws ReflectiveOperationException {
            var constructor = InMemoryLocationBackend.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            locationService = new LocationPlatformService(List.of(new RefLocationPlatform(constructor.newInstance())));
            identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "user-1").build();
        }
    }

    public static final class Mobile extends ConnectorMobileApi {
        public Mobile() {
            mobilePlatformService = new MobilePlatformService(List.of(new RefMobilePlatform(new InMemoryMobileBackend())));
        }
    }
}
