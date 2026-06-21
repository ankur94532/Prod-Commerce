package com.gocommerce.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private final Hybrid hybrid = new Hybrid();

    public Hybrid getHybrid() {
        return hybrid;
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
}
