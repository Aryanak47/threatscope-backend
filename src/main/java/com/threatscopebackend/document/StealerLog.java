package com.threatscope.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "stealer_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "login_password_url_unique", 
               def = "{'login': 1, 'password': 1, 'url': 1}", 
               unique = true)
public class StealerLog {
    @Id
    private String id;

    @Field("login")
    private String login;

    @Field("password") 
    private String password;

    @Field("url")
    private String url;

    @Field("timestamp")
    private LocalDateTime timestamp;

    @Field("metadata")
    private Map<String, Object> metadata;

    @Field("domain")
    private String domain;

    @Field("is_email")
    private Boolean isEmail;

    @Field("username")
    private String username;

    @Field("email_domain")
    private String emailDomain;

    @Field("processed")
    private Boolean processed = false;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("severity")
    private String severity; // Possible values: LOW, MEDIUM, HIGH, CRITICAL
}
