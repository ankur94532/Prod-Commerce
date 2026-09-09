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
        return success(indexed, catalogProducts, indexedDocuments, catalogProducts);
    }

    /**
     * @param expectedCatalogProducts the total the catalog said it had. Comparing only the
     *        pages we actually received against each other cannot detect a truncated
     *        pagination: every count agrees, and the rebuild is short.
     */
    public static ReindexResult success(int indexed, int catalogProducts, long indexedDocuments,
                                        long expectedCatalogProducts) {
        boolean consistent = indexed == catalogProducts
                && indexedDocuments == indexed
                && (expectedCatalogProducts <= 0 || expectedCatalogProducts == indexed);
        return new ReindexResult(indexed, catalogProducts, indexedDocuments, consistent, "ok", null);
    }

    public static ReindexResult failure(String message) {
        return new ReindexResult(0, 0, 0, false, "failed", message);
    }
}
