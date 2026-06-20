package com.gocommerce.search.config;

import com.gocommerce.search.service.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexBootstrap {

    private final SearchService searchService;
    private final boolean reindexOnStartup;

    public SearchIndexBootstrap(
            SearchService searchService,
            @Value("${search.bootstrap.reindex-empty-on-startup:true}") boolean reindexOnStartup) {
        this.searchService = searchService;
        this.reindexOnStartup = reindexOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapSearchIndex() {
        if (!reindexOnStartup) {
            return;
        }
        searchService.bootstrapIndexIfEmpty();
    }
}
