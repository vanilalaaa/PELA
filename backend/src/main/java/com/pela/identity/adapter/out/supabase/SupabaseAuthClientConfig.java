package com.pela.identity.adapter.out.supabase;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
class SupabaseAuthClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    SupabaseAuthRestAdapter supabaseAuthRestAdapter(RestClient.Builder builder, SupabaseProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient client = builder.clone()
                .baseUrl(properties.authBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new SupabaseAuthRestAdapter(client, properties);
    }
}
