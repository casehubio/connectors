package io.casehub.connectors.commerce.amazon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmazonPaApiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapItemToProductExtractsAllFields() throws Exception {
        var json = MAPPER.readTree("""
            {
              "ASIN": "B08N5WRWNW",
              "DetailPageURL": "https://www.amazon.co.uk/dp/B08N5WRWNW",
              "ItemInfo": {
                "Title": {"DisplayValue": "Wireless Headphones"},
                "ByLineInfo": {"Brand": {"DisplayValue": "SoundMax"}},
                "Classifications": {"Binding": {"DisplayValue": "Electronics"}}
              },
              "Offers": {
                "Listings": [{"Price": {"Amount": 29.99, "Currency": "GBP"}}]
              },
              "Images": {
                "Primary": {"Large": {"URL": "https://images.amazon.co.uk/headphones.jpg"}}
              }
            }
            """);

        var product = AmazonPaApiClient.mapItem(json);

        assertThat(product.id()).isEqualTo("B08N5WRWNW");
        assertThat(product.name()).isEqualTo("Wireless Headphones");
        assertThat(product.brand()).isEqualTo("SoundMax");
        assertThat(product.category()).isEqualTo("Electronics");
        assertThat(product.price().amount()).isEqualByComparingTo(new BigDecimal("29.99"));
        assertThat(product.price().currency()).isEqualTo("GBP");
        assertThat(product.thumbnailUrl()).isEqualTo("https://images.amazon.co.uk/headphones.jpg");
        assertThat(product.inStock()).isTrue();
    }

    @Test
    void mapItemHandlesMinimalFields() throws Exception {
        var json = MAPPER.readTree("""
            {
              "ASIN": "B000TEST",
              "ItemInfo": {
                "Title": {"DisplayValue": "Basic Item"}
              }
            }
            """);

        var product = AmazonPaApiClient.mapItem(json);

        assertThat(product.id()).isEqualTo("B000TEST");
        assertThat(product.name()).isEqualTo("Basic Item");
        assertThat(product.brand()).isEmpty();
        assertThat(product.category()).isEmpty();
        assertThat(product.price()).isNull();
        assertThat(product.thumbnailUrl()).isNull();
    }

    @Test
    void mapItemDetailExtractsAllFields() throws Exception {
        var json = MAPPER.readTree("""
            {
              "ASIN": "B08N5WRWNW",
              "DetailPageURL": "https://www.amazon.co.uk/dp/B08N5WRWNW",
              "ItemInfo": {
                "Title": {"DisplayValue": "Wireless Headphones"},
                "ByLineInfo": {"Brand": {"DisplayValue": "SoundMax"}},
                "Classifications": {"Binding": {"DisplayValue": "Electronics"}},
                "Features": {"DisplayValues": ["Noise cancelling", "40h battery"]}
              },
              "Offers": {
                "Listings": [{
                  "Price": {"Amount": 29.99, "Currency": "GBP"},
                  "Availability": {"Message": "In Stock"}
                }]
              },
              "Images": {
                "Primary": {"Large": {"URL": "https://images.amazon.co.uk/main.jpg"}},
                "Variants": [{"Large": {"URL": "https://images.amazon.co.uk/side.jpg"}}]
              },
              "CustomerReviews": {"Count": 142, "StarRating": {"Value": 4.3}}
            }
            """);

        var detail = AmazonPaApiClient.mapItemDetail(json);

        assertThat(detail.id()).isEqualTo("B08N5WRWNW");
        assertThat(detail.name()).isEqualTo("Wireless Headphones");
        assertThat(detail.url()).isEqualTo("https://www.amazon.co.uk/dp/B08N5WRWNW");
        assertThat(detail.reviewCount()).isEqualTo(142);
        assertThat(detail.rating()).isEqualTo(4.3);
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.specifications()).containsEntry("feature_0", "Noise cancelling");
        assertThat(detail.inStock()).isTrue();
    }

    @Test
    void buildSearchRequestBodyContainsRequiredFields() {
        var body = AmazonPaApiClient.buildSearchBody("laptop", "test-21", "www.amazon.co.uk");

        assertThat(body)
            .contains("\"Keywords\":\"laptop\"")
            .contains("\"PartnerTag\":\"test-21\"")
            .contains("\"Marketplace\":\"www.amazon.co.uk\"")
            .contains("\"PartnerType\":\"Associates\"")
            .contains("\"SearchIndex\":\"All\"");
    }
}
