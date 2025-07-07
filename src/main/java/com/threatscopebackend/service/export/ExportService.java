//package com.threatscope.service.export;
//
//import com.threatscope.dto.SearchRequest;
//import com.threatscope.dto.SearchResponse;
//import com.threatscope.security.UserPrincipal;
//import com.threatscope.service.SearchService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class ExportService {
//
//    private final SearchService searchService;
//
//    public byte[] exportSearchResults(SearchRequest request, String format, UserPrincipal user) {
//        // Get search results
//        SearchResponse results = searchService.search(request, user);
//
//        return switch (format.toUpperCase()) {
//            case "CSV" -> exportToCSV(results);
//            case "JSON" -> exportToJSON(results);
//            case "PDF" -> exportToPDF(results);
//            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
//        };
//    }
//
//    private byte[] exportToCSV(SearchResponse results) {
//        StringBuilder csv = new StringBuilder();
//        csv.append("ID,Email,URL,Date Discovered,Has Password\n");
//
//        results.getResults().forEach(result -> {
//            csv.append(String.format("%s,%s,%s,%s,%s\n",
//                    result.getId(),
//                    escapeCsv(result.getEmail()),
//                    escapeCsv(result.getUrl()),
//                    result.getDateDiscovered(),
//                    result.isHasPassword()
//            ));
//        });
//
//        return csv.toString().getBytes();
//    }
//
//    private byte[] exportToJSON(SearchResponse results) {
//        // Use Jackson ObjectMapper to convert to JSON
//        try {
//            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
//            return mapper.writeValueAsBytes(results);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to export to JSON", e);
//        }
//    }
//
//    private byte[] exportToPDF(SearchResponse results) {
//        // Implement PDF export using iText or similar
//        throw new UnsupportedOperationException("PDF export not implemented yet");
//    }
//
//    private String escapeCsv(String value) {
//        if (value == null) return "";
//        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
//            return "\"" + value.replace("\"", "\"\"") + "\"";
//        }
//        return value;
//    }
//}
