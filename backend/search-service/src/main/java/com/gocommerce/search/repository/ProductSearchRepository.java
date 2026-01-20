package com.gocommerce.search.repository;

import com.gocommerce.search.model.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    // OR search: name matches OR category matches
    @Query("""
            {
              "bool": {
                "should": [
                  { "match": { "name":    { "query": "?0" } } },
                  { "match": { "category":{ "query": "?1" } } }
                ]
              }
            }
            """)
    Page<ProductDocument> searchByNameOrCategory(
            String name, String category, Pageable pageable);

    // AND search: name matches AND category matches
    @Query("""
            {
              "bool": {
                "must": [
                  { "match": { "name":    { "query": "?0" } } },
                  { "match": { "category":{ "query": "?1" } } }
                ]
              }
            }
            """)
    Page<ProductDocument> searchByNameAndCategory(
            String name, String category, Pageable pageable);
}
