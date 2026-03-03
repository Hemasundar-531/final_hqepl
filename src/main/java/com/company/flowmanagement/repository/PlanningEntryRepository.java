package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.PlanningEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlanningEntryRepository extends MongoRepository<PlanningEntry, String> {
    List<PlanningEntry> findByFolderIdOrderByCreatedAtAsc(String folderId);

    PlanningEntry findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(String folderId, String orderId);
}
