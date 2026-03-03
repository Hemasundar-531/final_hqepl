package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.OrderEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderEntryRepository extends MongoRepository<OrderEntry, String> {
    List<OrderEntry> findByFolderIdOrderByCreatedAtDesc(String folderId);
    OrderEntry findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(String folderId, String orderId);
}
