package com.leslie.teachtrack.auth;

import java.util.Set;

public record AuthPrincipal(String userId, String tokenId, Set<String> scopes) {
    public boolean hasScope(String scope) {
        return scopes.contains("owner") || scopes.contains(scope);
    }
}
