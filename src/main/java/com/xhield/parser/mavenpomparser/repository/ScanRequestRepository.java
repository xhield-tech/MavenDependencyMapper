package com.xhield.parser.mavenpomparser.repository;

import com.xhield.parser.mavenpomparser.data.ScanRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScanRequestRepository extends MongoRepository<ScanRequest, String> {
    // The default findById(String id) and save(...) methods are inherited.
}
