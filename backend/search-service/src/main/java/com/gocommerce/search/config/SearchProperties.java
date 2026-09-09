package com.gocommerce.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private final Hybrid hybrid = new Hybrid();
    private final Rrf rrf = new Rrf();
    public Rrf getRrf() { return rrf; }

    private final Ann ann = new Ann();
    public Ann getAnn() { return ann; }

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

    public static class Rrf {
        private int candidateWindow = 100;
        private int rankConstant = 60;
        public int getCandidateWindow() { return candidateWindow; }
        public void setCandidateWindow(int value) {
            if (value < 1 || value > 1000) throw new IllegalArgumentException("RRF candidate window must be 1–1000");
            candidateWindow = value;
        }
        public int getRankConstant() { return rankConstant; }
        public void setRankConstant(int value) {
            if (value < 1) throw new IllegalArgumentException("RRF rank constant must be positive");
            rankConstant = value;
        }
    }

    public static class Ann {
        private int numCandidates = 100;

        public int getNumCandidates() {
            return numCandidates;
        }

        public void setNumCandidates(int numCandidates) {
            if (numCandidates < 1 || numCandidates > 10000) {
                throw new IllegalArgumentException("ANN num-candidates must be 1–10000");
            }
            this.numCandidates = numCandidates;
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
