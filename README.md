# oracle-database-embedding-flow

This code sample illustrates how to:
1. Stream documents OCI object storage, see [OCIDocumentLoader](src/main/java/com/example/documentloader/OCIDocumentLoader.java)
2. Split those documents into chunks, see [Splitter](src/main/java/com/example/splitter)
3. Embed document chunks using OCI GenAI Embeddings, [OCIEmbeddingModel](src/main/java/com/example/embeddingmodel/OCIEmbeddingModel.java)
4. Store the resulting embeddings as vectors in Oracle Database, see [OracleVectorStore](src/main/java/com/example/vectorstore/OracleVectorStore.java)

An example workflow ([EmbeddingWorkflowIT](src/test/java/com/example/EmbedddingWorkflowIT.java)) ties these steps together, a snippet of which is shown below:

```java
// Stream documents from OCI object storage.
documentLoader.streamDocuments(BUCKET_NAME, OBJECT_PREFIX)
    // Split each object storage document into chunks.
    .map(splitter::split)
    // Embed each chunk list using OCI GenAI service.
    .map(embeddingModel::embedAll)
    // Store embeddings in Oracle Database 23ai.
    .forEach(vectorStore::addAll);
```

## Run the test

The sample test loads documents from an object storage bucket named "mybucket" using the object prefix "documents". These documents are then embedded using the OCI GenAI service, and finally stored in a local Oracle Database container.

```shell
# Set your OCI compartment and namespace before running the t
export OCI_COMPARTMENT="my compartment OCID"
export OCI_NAMESPACE="my oci namespace"
mvn integration-test
```

It should take about 30-40 seconds to run the test, which asserts that vector have been successfully added to the database:.

```shell
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.75 s -- in com.example.EmbedddingWorkflowIT
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  36.016 s
```