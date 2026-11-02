package com.pela.identity.adapter.out.supabase;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình gọi Supabase Auth từ backend.
 *
 * @param url            {@code SUPABASE_URL}, ví dụ https://&lt;project-ref&gt;.supabase.co
 * @param anonKey        {@code SUPABASE_ANON_KEY} — header apikey cho các endpoint auth công khai
 * @param serviceRoleKey {@code SUPABASE_SERVICE_ROLE_KEY} — chỉ cho Admin API (xoá user); không bao giờ gửi cho client
 */
@ConfigurationProperties(prefix = "pela.supabase")
public record SupabaseProperties(String url, String anonKey, String serviceRoleKey) {

    public SupabaseProperties {
        url = url == null ? "" : url.trim().replaceAll("/+$", "");
        anonKey = anonKey == null ? "" : anonKey.trim();
        serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey.trim();
    }

    public String authBaseUrl() {
        return url + "/auth/v1";
    }

    @Override
    public String toString() {
        return "SupabaseProperties[url=" + url + "]";
    }
}
