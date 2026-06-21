package com.gocommerce.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private final Hybrid hybrid = new Hybrid();
    private final Indexing indexing = new Indexing();

    public Hybrid getHybrid() {
        return hybrid;
    }

    public Indexing getIndexing() {
        return indexing;
    }

    public static class Hybrid {
        private double keywordWeight = 1.0;
        private double vectorWeight = 1.5;

        public double getKeywordWeight() {
            return keywordWeight;
        }

        public void setKeywordWeight(double keywordWeight) {
            this.keywordWeight = keywordWeight;
        }

        public double getVectorWeight() {
            return vectorWeight;
        }

        public void setVectorWeight(double vectorWeight) {
            this.vectorWeight = vectorWeight;
        }
    }

    public static class Indexing {
        private int batchSize = 128;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
