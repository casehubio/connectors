package io.casehub.connectors.graphql;

import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.calendar.ref.InMemoryCalendarBackend;
import io.casehub.connectors.calendar.ref.RefCalendarPlatform;
import io.casehub.connectors.commerce.ref.InMemoryCommerceBackend;
import io.casehub.connectors.commerce.ref.RefCommercePlatform;
import io.casehub.connectors.commerce.spi.CommercePlatformService;
import io.casehub.connectors.contacts.ref.InMemoryContactsBackend;
import io.casehub.connectors.contacts.ref.RefContactsPlatform;
import io.casehub.connectors.contacts.spi.ContactsPlatformService;
import io.casehub.connectors.document.ref.InMemoryDocumentBackend;
import io.casehub.connectors.document.ref.RefDocumentPlatform;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.connectors.health.ref.InMemoryHealthBackend;
import io.casehub.connectors.health.ref.RefHealthPlatform;
import io.casehub.connectors.health.spi.HealthPlatformService;
import io.casehub.connectors.location.ref.InMemoryLocationBackend;
import io.casehub.connectors.location.ref.RefLocationPlatform;
import io.casehub.connectors.location.spi.LocationPlatformService;
import io.casehub.connectors.mobile.ref.InMemoryMobileBackend;
import io.casehub.connectors.mobile.ref.RefMobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatformService;
import io.casehub.connectors.travel.ref.InMemoryTravelBackend;
import io.casehub.connectors.travel.ref.RefTravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;

import java.util.List;
import java.util.Set;

public final class PlaybookApiServices {

    private static final CurrentPrincipal TEST_PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "user-1"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "default"; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

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

    public static final class Calendar extends ConnectorCalendarApi {
        public Calendar() throws ReflectiveOperationException {
            var constructor = InMemoryCalendarBackend.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            calendarService = new CalendarPlatformService(List.of(new RefCalendarPlatform(constructor.newInstance())));
            currentPrincipal = TEST_PRINCIPAL;
        }
    }

    public static final class Commerce extends ConnectorCommerceApi {
        public Commerce() throws ReflectiveOperationException {
            var constructor = InMemoryCommerceBackend.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            commerceService = new CommercePlatformService(List.of(new RefCommercePlatform(constructor.newInstance())));
            identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "user-1").build();
        }
    }

    public static final class Contacts extends ConnectorContactsApi {
        public Contacts() throws ReflectiveOperationException {
            var constructor = InMemoryContactsBackend.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            platformService = new ContactsPlatformService(List.of(new RefContactsPlatform(constructor.newInstance())));
            identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "user-1").build();
        }
    }

    public static final class Document extends ConnectorDocumentApi {
        public Document() {
            documentService = new DocumentPlatformService(List.of(new RefDocumentPlatform(new InMemoryDocumentBackend())));
            currentPrincipal = TEST_PRINCIPAL;
        }
    }

    public static final class Health extends ConnectorHealthApi {
        public Health() {
            healthService = new HealthPlatformService(List.of(new RefHealthPlatform(new InMemoryHealthBackend())));
            identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "user-1").build();
        }
    }

    public static final class Travel extends ConnectorTravelApi {
        public Travel() {
            travelService = new TravelPlatformService(List.of(new RefTravelPlatform(new InMemoryTravelBackend())));
            identity = QuarkusSecurityIdentity.builder().setPrincipal(() -> "user-1").build();
        }
    }
}
