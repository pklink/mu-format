package net.einself.mu.searchcontext.api;

import net.einself.mu.collection.api.CollectionRoot;
import org.jmolecules.ddd.annotation.Service;

import java.util.List;

@Service
public interface SearchService {

    List<SearchResult> search(String query, SearchOptions options, CollectionRoot root);
}
