package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String uploadDir;

    private Jwt jwt = new Jwt();

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public static class Jwt {
        private String secret;
        private long expiration;

        public String getSecret() { return secret; }
        public void setSecret(String secret) {
            this.secret = secret;
        }
        public long getExpiration() { return expiration; }
        public void setExpiration(long expiration) {
            this.expiration = expiration;
        }
    }
}
