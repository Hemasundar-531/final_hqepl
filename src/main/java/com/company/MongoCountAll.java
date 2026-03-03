package com.company;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;

public class MongoCountAll {
    public static void main(String[] args) {
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("flowmanagement");
            MongoIterable<String> collectionNames = database.listCollectionNames();
            for (String name : collectionNames) {
                long count = database.getCollection(name).countDocuments();
                System.out.println("Collection: " + name + ", Count: " + count);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
