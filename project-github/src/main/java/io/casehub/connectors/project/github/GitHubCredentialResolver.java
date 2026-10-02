package io.casehub.connectors.project.github;

public interface GitHubCredentialResolver {
    String resolveToken(String userId);
}
