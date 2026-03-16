# Spring AI Solr Vector Store

The Solr Vector Store implementation provides efficient vector similarity search using Apache Solr's `DenseVectorField` and `{!vectorSimilarity}` query parser.

## Requirements

* **Apache Solr 9.0 or later**: Dense vector search was introduced in Solr 9.
* **Vector Search Module**: The `vector-search` module must be enabled in your Solr installation.
* **Schema**: The vector store can automatically initialize the required schema, including the `DenseVectorField` type and necessary fields.

## Dependencies

Add the following dependency to your project:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-solr-store</artifactId>
</dependency>
```

## Usage

### Basic Initialization

To create a `SolrVectorStore`, you need a `SolrClient` (either `Http2SolrClient` or `CloudSolrClient`) and an `EmbeddingModel`.

```java
@Bean
public VectorStore vectorStore(SolrClient solrClient, EmbeddingModel embeddingModel) {
    return SolrVectorStore.builder(embeddingModel)
        .http2SolrClientBuilder(new Http2SolrClient.Builder("http://localhost:8983/solr"))
        .initializeSchema(true)
        .build();
}
```

### Advanced Configuration

You can customize the index name, similarity function, and dimensions using `SolrVectorStoreOptions`.

```java
@Bean
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    SolrVectorStoreOptions options = new SolrVectorStoreOptions();
    options.setIndexName("custom-index");
    options.setSimilarity(SimilarityFunction.cosine); // Support for cosine, euclidean, dot_product
    options.setDimensions(1536);

    return SolrVectorStore.builder(embeddingModel)
        .http2SolrClientBuilder(new Http2SolrClient.Builder("http://localhost:8983/solr"))
        .options(options)
        .initializeSchema(true)
        .build();
}
```

### Similarity Search with Filters

The Solr Vector Store supports Spring AI's filtering expressions, which are converted to Solr filter queries (`fq`).

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.query("Spring AI")
        .withTopK(5)
        .withSimilarityThreshold(0.7)
        .withFilterExpression("genre == 'tech' && year >= 2020")
);
```

## Configuration Options

| Option | Description | Default Value |
| --- | --- | --- |
| `indexName` | The name of the Solr collection | `spring-ai-document-index` |
| `dimensions` | The number of dimensions for the vector field | `1536` |
| `similarity` | The similarity function (`cosine`, `euclidean`, `dot_product`) | `cosine` |

## Documentation

For more detailed information, please refer to the [Spring AI Vector Store Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/solr.html).
