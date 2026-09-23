package io.casehub.connectors.bank.truelayer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "truelayer_consent_token")
public class ConsentTokenEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "access_token_expiry", nullable = false)
    private Instant accessTokenExpiry;

    @Column(name = "consent_expiry", nullable = false)
    private Instant consentExpiry;

    @Column(name = "scopes", nullable = false)
    private String scopes;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected ConsentTokenEntity() {}

    public ConsentTokenEntity(String userId, String accessToken,
                               String refreshToken, Instant accessTokenExpiry,
                               Instant consentExpiry, String scopes,
                               Instant grantedAt) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiry = accessTokenExpiry;
        this.consentExpiry = consentExpiry;
        this.scopes = scopes;
        this.grantedAt = grantedAt;
    }

    public String getUserId() { return userId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getAccessTokenExpiry() { return accessTokenExpiry; }
    public Instant getConsentExpiry() { return consentExpiry; }
    public String getScopes() { return scopes; }
    public Instant getGrantedAt() { return grantedAt; }

    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public void setAccessTokenExpiry(Instant accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }
    public void setConsentExpiry(Instant consentExpiry) { this.consentExpiry = consentExpiry; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
