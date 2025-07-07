package com.threatscope.elasticsearch;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.time.LocalDateTime;
import java.util.Map;

@Document(indexName = "#{@indexNameProvider.getCurrentMonthIndex()}")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreachDataIndex {
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String login;

    @Field(type = FieldType.Text)
    private String password;

    @Field(type = FieldType.Text)
    private String url;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private LocalDateTime timestamp;

    @Field(type = FieldType.Object, enabled = true)
    private Map<String, Object> metadata;
}
