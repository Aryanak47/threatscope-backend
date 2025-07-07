package com.threatscopebackend.service.data;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component("indexNameProvider")
public class IndexNameProvider {
    
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    public String getCurrentMonthIndex() {
        return "breaches-" + LocalDateTime.now().format(MONTH_FORMATTER);
    }
    
    public String getIndexForDate(LocalDateTime date) {
        return "breaches-" + date.format(MONTH_FORMATTER);
    }
    
    public String[] getAllIndicesPattern() {
        return new String[]{"breaches-*"};
    }
    
    public String[] getIndicesForLastMonths(int monthsBack) {
        if (monthsBack <= 0) {
            return getAllIndicesPattern();
        }
        
        String[] indices = new String[monthsBack];
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < monthsBack; i++) {
            LocalDateTime month = now.minusMonths(i);
            indices[i] = "breaches-" + month.format(MONTH_FORMATTER);
        }
        
        return indices;
    }
}
