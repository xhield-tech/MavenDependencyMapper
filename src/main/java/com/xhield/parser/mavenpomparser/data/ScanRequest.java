package com.xhield.parser.mavenpomparser.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scan_requests")
public class ScanRequest {
    @Id
    private String id; // SHA-256 hash of pomContent
    private String pomContent;
    private String status; // SUBMITTED, PROCESSING, COMPLETED, FAILED
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private DependencyInfo result;
    private String message;
}
