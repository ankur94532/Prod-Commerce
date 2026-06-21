package com.gocommerce.search.service;

public record ReindexResult(
        int indexed,
        int catalogProducts,
        long indexedDocuments,
        boolean consistent,
        String status,
        String message
) {
    public static ReindexResult success(int indexed, int catalogProducts, long indexedDocuments) {
        return new ReindexResult(
                indexed,
                catalogProducts,
                indexedDocuments,
                indexed == catalogProducts && indexedDocuments == indexed,
                "ok",
                null);
    }

    public static ReindexResult failure(String message) {
        return new ReindexResult(0, 0, 0, false, "failed", message);
    }
}
