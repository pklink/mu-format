package net.einself.mu.searchcontext.api;

import java.util.List;

public record SearchData(
        int total,
        List<SearchResultItem> releases,
        List<SearchResultItem> artists,
        List<SearchResultItem> tracks) {}
