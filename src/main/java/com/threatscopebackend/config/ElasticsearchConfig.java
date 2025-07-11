package com.threatscopebackend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.threatscopebackend.repository.elasticsearch")
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.connection-timeout:5s}")
    private Duration connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:30s}")
    private Duration socketTimeout;

    @Value("${app.elasticsearch.index.prefix:breaches-}")
    private String indexPrefix;

    @Bean
    public RestClient restClient() {
        HttpHost httpHost = HttpHost.create(elasticsearchUri);

        RestClientBuilder builder = RestClient.builder(httpHost);

        // Configure timeouts
        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()))
                        .setSocketTimeout(Math.toIntExact(socketTimeout.toMillis()))
                        .setConnectionRequestTimeout(Math.toIntExact(connectionTimeout.toMillis()))
        );

        // Configure authentication if provided
        if (!username.isEmpty() && !password.isEmpty()) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
            );

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        return builder.build();
    }

    @Bean
    public ElasticsearchTransport transport(RestClient restClient, ObjectMapper objectMapper) {
        // Create a custom ObjectMapper for Elasticsearch with JavaTime support
        ObjectMapper elasticsearchMapper = objectMapper.copy();
        elasticsearchMapper.registerModule(new JavaTimeModule());

        return new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(elasticsearchMapper)
        );
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    public ElasticsearchOperations elasticsearchTemplate(ElasticsearchClient client) {
        return new ElasticsearchTemplate(client);
    }

    // Optional: Custom converter configuration
    @Bean
    @ConditionalOnProperty(value = "elasticsearch.custom-converter", havingValue = "true", matchIfMissing = false)
    public ElasticsearchConverter elasticsearchConverter(SimpleElasticsearchMappingContext mappingContext) {
        // Add any custom conversions here if needed
        return new MappingElasticsearchConverter(mappingContext);
    }

    /**
     * Gets the current month's index name in format 'breaches-YYYY-MM'
     * @return the current month's index name
     */
    public String getCurrentMonthIndex() {
        return indexPrefix + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

}