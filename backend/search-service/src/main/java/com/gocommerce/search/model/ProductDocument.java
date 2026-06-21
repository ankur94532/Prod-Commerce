package com.gocommerce.search.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.List;

@Document(indexName = "products")
public class ProductDocument {

    public static final int SEARCH_EMBEDDING_DIMENSIONS = 384;

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String nameSort;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;

    @Field(type = FieldType.Integer)
    private Integer stockQuantity;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String fit;

    @Field(type = FieldType.Keyword)
    private String storage;

    @Field(type = FieldType.Keyword)
    private String memory;

    @Field(type = FieldType.Keyword)
    private String material;

    @Field(type = FieldType.Text)
    private String searchText;

    @Field(type = FieldType.Dense_Vector, dims = SEARCH_EMBEDDING_DIMENSIONS)
    private List<Float> searchEmbedding;

    @Field(type = FieldType.Long)
    private Long productId;

    // NEW: numeric popularity score used in function_score
    @Field(type = FieldType.Long)
    private Long popularityScore;

    public ProductDocument() {
    }

    // old ctor now delegates, keeping old call sites working
    public ProductDocument(String id,
                           String slug,
                           String name,
                           String category,
                           BigDecimal price,
                           String currency,
                           List<String> tags,
                           String thumbnailUrl) {
        this(id, slug, name, category, price, currency, tags, thumbnailUrl, 0L);
    }

    // new ctor with popularityScore
    public ProductDocument(String id,
                           String slug,
                           String name,
                           String category,
                           BigDecimal price,
                           String currency,
                           List<String> tags,
                           String thumbnailUrl,
                           Long popularityScore) {
        this(id, slug, name, null, null, category, null, price, currency, tags, thumbnailUrl,
                null, null, null, null, null, null, null, null, popularityScore);
    }

    public ProductDocument(String id,
                           String slug,
                           String name,
                           String description,
                           String brand,
                           String category,
                           Long productId,
                           BigDecimal price,
                           String currency,
                           List<String> tags,
                           String thumbnailUrl,
                           Integer stockQuantity,
                           String color,
                           String type,
                           String fit,
                           String storage,
                           String memory,
                           String material,
                           String searchText,
                           Long popularityScore) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.nameSort = name;
        this.description = description;
        this.brand = brand;
        this.category = category;
        this.productId = productId;
        this.price = price;
        this.currency = currency;
        this.tags = tags;
        this.thumbnailUrl = thumbnailUrl;
        this.stockQuantity = stockQuantity;
        this.color = color;
        this.type = type;
        this.fit = fit;
        this.storage = storage;
        this.memory = memory;
        this.material = material;
        this.searchText = searchText;
        this.popularityScore = popularityScore != null ? popularityScore : 0L;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameSort() { return nameSort; }
    public void setNameSort(String nameSort) { this.nameSort = nameSort; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFit() { return fit; }
    public void setFit(String fit) { this.fit = fit; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }

    public List<Float> getSearchEmbedding() { return searchEmbedding; }
    public void setSearchEmbedding(List<Float> searchEmbedding) { this.searchEmbedding = searchEmbedding; }

    public Long getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Long popularityScore) {
        this.popularityScore = popularityScore != null ? popularityScore : 0L;
    }
}
