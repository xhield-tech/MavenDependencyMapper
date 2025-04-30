package com.xhield.parser.mavenpomparser.controller;

import com.xhield.parser.mavenpomparser.data.DependencyInfo;
import com.xhield.parser.mavenpomparser.data.ScanRequest;
import com.xhield.parser.mavenpomparser.repository.ScanRequestRepository;
import com.xhield.parser.mavenpomparser.service.PomParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/pom-parser")
public class PomController {
    @Autowired
    private PomParserService parserService;
    @Autowired
    private ScanRequestRepository scanRequestRepository;

    @PostMapping("/fetch-all-dependencies-pom")
    public ResponseEntity<?> fetchAllDependenciesPom(@RequestBody String pomContent,
                                                     @RequestParam(defaultValue = "false") boolean forceScan) {

        try {
            String requestId = generateRequestId(pomContent);
            Optional<ScanRequest> existing = scanRequestRepository.findById(requestId);
            if (existing.isPresent()) {
                ScanRequest request = existing.get();
                if (!forceScan && "COMPLETED".equals(request.getStatus())) {
                    return ResponseEntity.ok(Map.of(
                            "requestId", requestId,
                            "status", request.getStatus(),
                            "note", "Using cached result"
                    ));
                }
            }

            // Save SUBMITTED state in DB
            ScanRequest scanRequest = new ScanRequest();
            scanRequest.setId(requestId);
            scanRequest.setPomContent(pomContent);
            scanRequest.setStatus("SUBMITTED");
            scanRequest.setMessage("Scan request submitted");
            scanRequest.setSubmittedAt(LocalDateTime.now());

            scanRequestRepository.save(scanRequest);

            // Trigger background processing
            parserService.processPomAsync(scanRequest, forceScan);

            // Return immediately with requestId
            return ResponseEntity.ok(scanRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @GetMapping("/scan-status/{requestId}")
    public ResponseEntity<?> getScanRequestStatus(@PathVariable String requestId) {
        Optional<ScanRequest> existing = scanRequestRepository.findById(requestId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Scan request not found"));
        }

        ScanRequest scanRequest = existing.get();

        // You can create a response DTO if you don't want to expose everything
        return ResponseEntity.ok(scanRequest);
    }

    public String generateRequestId(String pomContent) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(pomContent.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
