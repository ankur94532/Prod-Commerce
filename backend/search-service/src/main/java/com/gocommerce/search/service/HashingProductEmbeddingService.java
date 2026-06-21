package com.gocommerce.search.service;

import com.gocommerce.search.model.ProductDocument;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class HashingProductEmbeddingService implements ProductEmbeddingService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int dimensions;

    public HashingProductEmbeddingService() {
        this(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS);
    }

    HashingProductEmbeddingService(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<Float> embed(String text) {
        float[] vector = new float[dimensions];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return zeroVector();
        }

        for (String token : tokens) {
            addFeature(vector, "t:" + token, 1.0f);
            addCharacterNgrams(vector, token);
        }
        for (int i = 0; i < tokens.size() - 1; i++) {
            addFeature(vector, "b:" + tokens.get(i) + "_" + tokens.get(i + 1), 0.75f);
        }

        normalize(vector);
        return toList(vector);
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return TOKEN_PATTERN.matcher(normalized)
                .results()
                .map(MatchResult::group)
                .toList();
    }

    private void addCharacterNgrams(float[] vector, String token) {
        if (token.length() < 3) {
            return;
        }
        for (int i = 0; i <= token.length() - 3; i++) {
            addFeature(vector, "g:" + token.substring(i, i + 3), 0.35f);
        }
    }

    private void addFeature(float[] vector, String feature, float weight) {
        long hash = fnv1a64(feature);
        int index = Math.floorMod(hash, dimensions);
        float sign = (hash & (1L << 63)) == 0 ? 1.0f : -1.0f;
        vector[index] += sign * weight;
    }

    private long fnv1a64(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private void normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0.0) {
            return;
        }

        float magnitude = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / magnitude;
        }
    }

    private List<Float> zeroVector() {
        List<Float> vector = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            vector.add(0.0f);
        }
        return vector;
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
