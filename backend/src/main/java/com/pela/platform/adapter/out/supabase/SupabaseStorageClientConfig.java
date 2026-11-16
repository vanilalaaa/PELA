package com.pela.platform.adapter.out.supabase;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class SupabaseStorageClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Audio WAV vài MB: chờ lâu hơn Auth. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    SupabaseStorageRestAdapter supabaseStorageRestAdapter(RestClient.Builder builder,
            @Value("${pela.supabase.url:}") String url,
            @Value("${pela.supabase.service-role-key:}") String serviceRoleKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient client = builder.clone().requestFactory(requestFactory).build();
        return new SupabaseStorageRestAdapter(client, url.trim().replaceAll("/+$", ""), serviceRoleKey.trim());
    }
}
