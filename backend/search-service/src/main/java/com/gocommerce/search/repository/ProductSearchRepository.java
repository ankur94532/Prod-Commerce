package com.gocommerce.search.repository;

import com.gocommerce.search.model.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    // Relevance-oriented search: phrase + AND, with field boosts
    @Query("""
            {
              "bool": {
                "should": [
                  {
                    "match_phrase": {
                      "name": {
                        "query": "?0",
                        "boost": 5.0
                      }
                    }
                  },
                  {
                    "match": {
                      "name": {
                        "query": "?0",
                        "operator": "and",
                        "boost": 3.0
                      }
                    }
                  },
                  {
                    "match": {
                      "tags": {
                        "query": "?0",
                        "operator": "and",
                        "boost": 2.0
                      }
                    }
                  },
                  {
                    "match": {
                      "category": {
                        "query": "?1",
                        "operator": "and",
                        "boost": 1.0
                      }
                    }
                  }
                ]
              }
            }
            """)
    Page<ProductDocument> searchByNameOrCategory(
            String name, String category, Pageable pageable
    );

    // For explicit category filters (q + category selected on UI)
    @Query("""
            {
              "bool": {
                "must": [
                  {
                    "match": {
                      "name": {
                        "query": "?0",
                        "operator": "and"
                      }
                    }
                  },
                  {
                    "match": {
                      "category": {
                        "query": "?1",
                        "operator": "and"
                      }
                    }
                  }
                ]
              }
            }
            """)
    Page<ProductDocument> searchByNameAndCategory(
            String name, String category, Pageable pageable
    );
}
