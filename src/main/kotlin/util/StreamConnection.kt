package net.hellz.util

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.bson.Document

object StreamConnection {
    // Create the MongoDB client using your connection string.
    private val client: MongoClient = MongoClients.create(
        "mongodb+srv://mcnaryy:RCbIk6g7kzDPNAed@hellz.ffcrq.mongodb.net/?retryWrites=true&w=majority&appName=HellZ"
    )
    // Get the target database.
    private val database: MongoDatabase = client.getDatabase("clerk")

    init {
        println("Successfully connected to the database.")
    }

    // Helper function to get a collection.
    private fun getCollection(collectionName: String): MongoCollection<Document> {
        return database.getCollection(collectionName)
    }

    /**
     * Inserts a document into the given collection.
     */
    suspend fun writeAsync(collectionName: String, document: Document) {
        withContext(Dispatchers.IO) {
            try {
                getCollection(collectionName).insertOne(document).awaitSingle()
                println("[rStream] A document was inserted into the database.")
            } catch (e: Exception) {
                println("[rStream] Error inserting document: ${e.message}")
            }
        }
    }

    /**
     * Reads a single document from the given collection using a filter.
     */
    suspend fun readAsync(collectionName: String, filter: Document): Document? {
        return withContext(Dispatchers.IO) {
            try {
                getCollection(collectionName).find(filter).awaitFirstOrNull()
            } catch (e: Exception) {
                println("[rStream] Error reading document: ${e.message}")
                null
            }
        }
    }

    /**
     * Updates a document in the given collection.
     *
     * NOTE: The update document must include its own update operator (e.g. "$set", "$addToSet", "$pull").
     */
    suspend fun updateAsync(collectionName: String, filter: Document, update: Document) {
        withContext(Dispatchers.IO) {
            try {
                getCollection(collectionName).updateOne(filter, update).awaitSingle()
                println("[rStream] A document was successfully updated in the database!")
            } catch (e: Exception) {
                println("[rStream] Error updating document: ${e.message}")
            }
        }
    }

    /**
     * Reads all documents from the given collection.
     */
    suspend fun readAllAsync(collectionName: String): List<Document> {
        return withContext(Dispatchers.IO) {
            getCollection(collectionName)
                .find()
                .asFlow()
                .toList()
        }
    }

    /**
     * Deletes a document from the given collection.
     */
    suspend fun deleteAsync(collectionName: String, filter: Document) {
        withContext(Dispatchers.IO) {
            try {
                getCollection(collectionName).deleteOne(filter).awaitSingle()
                println("[rStream] A document was successfully deleted from the database!")
            } catch (e: Exception) {
                println("[rStream] Error deleting document: ${e.message}")
            }
        }
    }
}
