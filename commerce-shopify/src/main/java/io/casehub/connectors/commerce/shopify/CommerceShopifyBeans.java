package io.casehub.connectors.commerce.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class CommerceShopifyBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    ShopifyCommercePlatform shopifyCommercePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "shopify-storefront", "default")
            .orElseThrow(() -> new IllegalStateException(
                "No Shopify Storefront credentials configured — expected provider 'shopify-storefront'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            var client = new ShopifyStorefrontClient(
                cred.path("storeDomain").asText(),
                cred.path("storefrontAccessToken").asText());
            return new ShopifyCommercePlatform(client);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Shopify Storefront credentials", e);
        }
    }
}
