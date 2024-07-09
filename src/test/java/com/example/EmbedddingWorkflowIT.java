package com.example;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import com.example.documentloader.OCIDocumentLoader;
import com.example.embeddingmodel.OCIEmbeddingModel;
import com.example.splitter.LineSplitter;
import com.example.vectorstore.OracleVectorStore;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OCI_NAMESPACE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT", matches = ".+")
public class EmbedddingWorkflowIT {
    // We'll use the on-demand cohere light v2 model for embedding.
    private static final String EMBEDDING_MODEL_V2 = "cohere.embed-english-light-v2.0";
    // OCI Embedding service uses 1024 dimensional vectors.
    private static final int DIMENSIONS = 1024;
    private static final String TABLE = "vector_store";

    private static final String OCI_NAMESPACE = System.getenv("OCI_NAMESPACE");
    private static final String OCI_COMPARTMENT = System.getenv("OCI_COMPARTMENT");

    // We'll load all documents from a folder named "documents" in a bucket named "mybucket"
    private static final String BUCKET_NAME = "mybucket";
    private static final String OBJECT_PREFIX = "documents";


    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.4-slim-faststart
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
            .withUsername("testuser")
            .withPassword(("testpwd"));

    @Test
    void exampleEmbeddingWorkflow() throws Exception {
        // Configure a datasource for the Oracle container.
        var dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("VECTOR_SAMPLE");
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());


        // Create an OCI authentication provider using the default local config file.
        var authProvider = new ConfigFileAuthenticationDetailsProvider(
                Paths.get(System.getProperty("user.home"), ".oci", "config").toString(),
                "DEFAULT"
        );
        // Create an object storage document loader to load texts
        var documentLoader = new OCIDocumentLoader(
                ObjectStorageClient.builder().build(authProvider),
                OCI_NAMESPACE
        );
        // Create an OCI Embedding model for text embedding.
        var embeddingModel = OCIEmbeddingModel.builder()
                .model(EMBEDDING_MODEL_V2)
                .aiClient(GenerativeAiInferenceClient.builder().build(authProvider))
                .compartmentId(OCI_COMPARTMENT).build();
        // Create a OracleVectorSample instance.
        var vectorStore = new OracleVectorStore(dataSource, TABLE, DIMENSIONS);
        // Create the vector table in the database.
        vectorStore.createTableIfNotExists();
        var splitter = new LineSplitter();

        // Stream documents from OCI object storage.
        documentLoader.streamDocuments(BUCKET_NAME, OBJECT_PREFIX)
                // Split each object storage document into chunks.
                .map(splitter::split)
                // Embed each chunk list using OCI GenAI service.
                .map(embeddingModel::embedAll)
                // Store embeddings in Oracle Database 23ai.
                .forEach(vectorStore::addAll);

        // Assert records were added to database.
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("select id from vector_store");
            rs.next();
            assertThat(rs.getRow()).isGreaterThan(0);
        }
    }
}
