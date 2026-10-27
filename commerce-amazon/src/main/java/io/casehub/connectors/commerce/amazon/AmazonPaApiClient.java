package io.casehub.connectors.commerce.amazon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.Page;
import io.casehub.connectors.commerce.model.Money;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductImage;
import io.casehub.connectors.http.HttpHelper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class AmazonPaApiClient {

    private static final Logger LOG = Logger.getLogger(AmazonPaApiClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SERVICE = "ProductAdvertisingAPI";
    private static final String SEARCH_TARGET = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems";
    private static final String GET_TARGET = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.GetItems";

    private final String accessKey;
    private final String secretKey;
    private final String partnerTag;
    private final String host;
    private final String region;
    private final String marketplace;

    public AmazonPaApiClient(String accessKey, String secretKey, String partnerTag,
                              String host, String region, String marketplace) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.partnerTag = partnerTag;
        this.host = host;
        this.region = region;
        this.marketplace = marketplace;
    }

    public Page<Product> searchItems(String keywords) {
        var body = buildSearchBody(keywords, partnerTag, marketplace);
        var json = execute("/paapi5/searchitems", SEARCH_TARGET, body);
        if (json == null) return Page.of(List.of());

        var items = new ArrayList<Product>();
        var searchResult = json.path("SearchResult").path("Items");
        if (searchResult.isArray()) {
            for (var item : searchResult) {
                items.add(mapItem(item));
            }
        }
        return Page.of(items);
    }

    public ProductDetail getItem(String asin) {
        var body = buildGetBody(asin, partnerTag, marketplace);
        var json = execute("/paapi5/getitems", GET_TARGET, body);
        if (json == null) throw new RuntimeException("Failed to fetch product: " + asin);

        var items = json.path("ItemsResult").path("Items");
        if (!items.isArray() || items.isEmpty()) {
            throw new RuntimeException("Product not found: " + asin);
        }
        return mapItemDetail(items.get(0));
    }

    private JsonNode execute(String path, String target, String body) {
        try {
            var uri = URI.create("https://" + host + path);
            var headers = new TreeMap<>(Map.of(
                "content-type", "application/json; charset=UTF-8",
                "content-encoding", "amz-1.0",
                "x-amz-target", target
            ));
            var signed = AwsSigner.sign("POST", uri, headers, body,
                accessKey, secretKey, region, SERVICE, Instant.now());

            var builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            signed.forEach(builder::header);

            var response = HttpHelper.CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warning("Amazon PA-API error: " + path + " status=" + response.statusCode());
                return null;
            }
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            LOG.warning("Amazon PA-API request failed: " + path + " — " + e.getMessage());
            return null;
        }
    }

    static String buildSearchBody(String keywords, String partnerTag, String marketplace) {
        return "{\"Keywords\":" + q(keywords)
            + ",\"SearchIndex\":\"All\""
            + ",\"Resources\":[\"ItemInfo.Title\",\"ItemInfo.ByLineInfo\",\"ItemInfo.Classifications\","
            + "\"Offers.Listings.Price\",\"Offers.Listings.Availability\","
            + "\"Images.Primary.Large\"]"
            + ",\"PartnerTag\":" + q(partnerTag)
            + ",\"PartnerType\":\"Associates\""
            + ",\"Marketplace\":" + q(marketplace) + "}";
    }

    static String buildGetBody(String asin, String partnerTag, String marketplace) {
        return "{\"ItemIds\":[" + q(asin) + "]"
            + ",\"Resources\":[\"ItemInfo.Title\",\"ItemInfo.ByLineInfo\",\"ItemInfo.Classifications\","
            + "\"ItemInfo.Features\",\"ItemInfo.ProductInfo\","
            + "\"Offers.Listings.Price\",\"Offers.Listings.Availability\","
            + "\"Images.Primary.Large\",\"Images.Variants.Large\","
            + "\"CustomerReviews.Count\",\"CustomerReviews.StarRating\"]"
            + ",\"PartnerTag\":" + q(partnerTag)
            + ",\"PartnerType\":\"Associates\""
            + ",\"Marketplace\":" + q(marketplace) + "}";
    }

    static Product mapItem(JsonNode item) {
        var asin = item.path("ASIN").asText();
        var info = item.path("ItemInfo");
        var name = info.path("Title").path("DisplayValue").asText("");
        var brand = info.path("ByLineInfo").path("Brand").path("DisplayValue").asText("");
        var category = info.path("Classifications").path("Binding").path("DisplayValue").asText("");

        Money price = extractPrice(item);
        boolean inStock = price != null;

        var thumbnailUrl = item.path("Images").path("Primary").path("Large").path("URL").asText(null);

        return new Product(asin, name, brand, category, price, null, null, inStock, thumbnailUrl);
    }

    static ProductDetail mapItemDetail(JsonNode item) {
        var asin = item.path("ASIN").asText();
        var url = item.path("DetailPageURL").asText(null);
        var info = item.path("ItemInfo");
        var name = info.path("Title").path("DisplayValue").asText("");
        var brand = info.path("ByLineInfo").path("Brand").path("DisplayValue").asText("");
        var category = info.path("Classifications").path("Binding").path("DisplayValue").asText("");

        Money price = extractPrice(item);
        boolean inStock = item.path("Offers").path("Listings").has(0)
            && "In Stock".equalsIgnoreCase(
                item.at("/Offers/Listings/0/Availability/Message").asText(""));

        var images = new ArrayList<ProductImage>();
        var primaryUrl = item.path("Images").path("Primary").path("Large").path("URL").asText(null);
        if (primaryUrl != null) {
            images.add(new ProductImage(primaryUrl, name, 0, 0));
        }
        var variants = item.path("Images").path("Variants");
        if (variants.isArray()) {
            for (var v : variants) {
                var vUrl = v.path("Large").path("URL").asText(null);
                if (vUrl != null) images.add(new ProductImage(vUrl, null, 0, 0));
            }
        }

        var specs = new LinkedHashMap<String, String>();
        var features = info.path("Features").path("DisplayValues");
        if (features.isArray()) {
            for (int i = 0; i < features.size(); i++) {
                specs.put("feature_" + i, features.get(i).asText());
            }
        }

        var reviews = item.path("CustomerReviews");
        Integer reviewCount = reviews.has("Count") ? reviews.get("Count").asInt() : null;
        Double rating = reviews.has("StarRating")
            ? reviews.path("StarRating").path("Value").asDouble() : null;

        return new ProductDetail(asin, name, brand, null, category, null, price,
            rating, reviewCount, inStock, 0, images, List.of(), specs, url);
    }

    private static Money extractPrice(JsonNode item) {
        var listing = item.path("Offers").path("Listings");
        if (!listing.isArray() || listing.isEmpty()) return null;
        var priceNode = listing.get(0).path("Price");
        if (priceNode.isMissingNode()) return null;
        return new Money(
            new BigDecimal(priceNode.path("Amount").asText("0")),
            priceNode.path("Currency").asText("GBP"));
    }

    private static String q(String value) {
        return "\"" + HttpHelper.jsonEscape(value) + "\"";
    }
}
