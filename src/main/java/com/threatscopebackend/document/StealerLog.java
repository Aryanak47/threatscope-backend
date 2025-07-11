package com.threatscopebackend.document;

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
import java.util.regex.Pattern;

@Document(collection = "stealer_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "login_password_url_unique", 
               def = "{'login': 1, 'password': 1, 'url': 1}", 
               unique = true)
public class StealerLog {
    @Field("id")
    private String id;

    @Field("login")
    private String login;

    @Field("password") 
    private String password;

    @Field("url")
    private String url;


    @Field("metadata")
    private String metadata;

    @Field("domain")
    private String domain;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("source_db")
    private String source;

}
