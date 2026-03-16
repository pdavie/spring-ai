# IDOL Vector Store

The [IDOL](https://www.opentext.com/products/idol) vector store provides integration with Micro Focus / OpenText IDOL to store and search document embeddings.

## Installation

Add the following dependency to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-idol-store</artifactId>
</dependency>
```

Or for Gradle `build.gradle`:

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-idol-store'
}
```

## Configuration

To configure the `IdolVectorStore`, you need an instance of `IdolApi` and an `EmbeddingModel`.

```java
@Bean
public IdolApi idolApi() {
    return new IdolApi(
        "http://localhost:9000",   // IDOL Base URL (ACI port)
        "http://localhost:9001",   // IDOL Index URL (Index port)
        "http://localhost:9030",   // IDOL Community URL
        "MyDatabase"               // Database name
    );
}

@Bean
public IdolVectorStore vectorStore(IdolApi idolApi, EmbeddingModel embeddingModel) {
    return IdolVectorStore.builder(idolApi, embeddingModel)
        .vectorField("embedding") // Optional: name of the field to store vectors (defaults to "embedding")
        .build();
}
```

## Usage

### Adding Documents

```java
List<Document> documents = List.of(
    new Document("1", "Spring AI is awesome", Map.of("category", "tech")),
    new Document("2", "The weather is nice today", Map.of("category", "general"))
);

vectorStore.add(documents);
```

### Similarity Search

Standard similarity search using `SearchRequest`:

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("Spring AI")
        .topK(1)
        .build()
);
```

### Specialized IDOL Search

Use `IdolSearchRequest` for advanced IDOL-specific features like custom print options or overriding the vector field:

```java
IdolSearchRequest searchRequest = IdolSearchRequest.idolBuilder()
    .query("Spring AI")
    .topK(5)
    .print("PrintFields")
    .printFields("category,my_custom_field")
    .build();

List<Document> results = vectorStore.similaritySearch(searchRequest);
```

### Filtering with Security Info

IDOL supports security info for row-level security. You can pass a username via metadata filters, and the `IdolVectorStore` will automatically fetch the security info token from the IDOL Community component:

```java
Filter.Expression expression = new Filter.Expression(Filter.ExpressionType.EQ, 
    new Filter.Key(IdolVectorStore.METADATA_IDOL_USER), 
    new Filter.Value("john_doe"));

List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("secure content")
        .filterExpression(expression)
        .build()
);
```

## More Information

For more details on the IDOL vector store and other Spring AI features, please refer to the [official Spring AI documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/idol.html).
