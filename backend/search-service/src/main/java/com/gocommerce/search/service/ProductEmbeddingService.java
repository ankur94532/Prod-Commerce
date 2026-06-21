package com.gocommerce.search.service;

import java.util.List;

public interface ProductEmbeddingService {

    int dimensions();

    List<Float> embed(String text);

    default List<List<Float>> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
