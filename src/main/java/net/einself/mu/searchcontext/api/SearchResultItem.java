package net.einself.mu.searchcontext.api;

import java.util.Map;

public record SearchResultItem(
                                String id,
                                Map<String, String> fields,
                                String path) {
}
