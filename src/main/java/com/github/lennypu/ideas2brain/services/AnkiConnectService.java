package com.github.lennypu.ideas2brain.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Service for communicating with AnkiConnect API
 */
@Service(Service.Level.APP)
public final class AnkiConnectService {
    private static final Logger LOG = Logger.getInstance(AnkiConnectService.class);
    private static final String ANKI_CONNECT_URL = "http://localhost:8765";
    private static final Gson gson = new Gson();
    
    /**
     * Checks if AnkiConnect is available
     * 
     * @return true if AnkiConnect is available
     */
    public boolean isAnkiConnectAvailable() {
        try {
            JsonObject response = makeRequest("version", null);
            return response != null && response.has("result");
        } catch (IOException e) {
            LOG.warn("AnkiConnect is not available", e);
            return false;
        }
    }
    
    /**
     * Checks if a note exists in Anki
     * 
     * @param noteId The ID of the note to check
     * @return true if the note exists
     */
    public boolean doesNoteExist(String noteId) {
        try {
            JsonObject params = new JsonObject();
            params.addProperty("note", noteId);
            
            JsonObject response = makeRequest("notesInfo", params);
            return response != null && response.has("result") && !response.get("result").isJsonNull();
        } catch (IOException e) {
            LOG.warn("Failed to check if note exists", e);
            return false;
        }
    }
    
    /**
     * Creates a deck if it doesn't exist
     * 
     * @param deckName The name of the deck to create
     * @return true if the deck was created or already exists
     */
    public boolean createDeckIfNotExists(String deckName) {
        try {
            JsonObject params = new JsonObject();
            params.addProperty("deck", deckName);
            
            JsonObject response = makeRequest("createDeck", params);
            return response != null && response.has("result");
        } catch (IOException e) {
            LOG.warn("Failed to create deck", e);
            return false;
        }
    }
    
    /**
     * Adds a note to Anki
     * 
     * @param deckName The name of the deck to add the note to
     * @param front The front of the card
     * @param back The back of the card
     * @param tags List of tags to add to the note
     * @param sourceFilePath The path to the source file (used as a unique identifier)
     * @return The ID of the created note, or null if creation failed
     */
    @Nullable
    public String addNote(String deckName, String front, String back, List<String> tags, String sourceFilePath) {
        try {
            // Create deck if it doesn't exist
            createDeckIfNotExists(deckName);
            
            JsonObject note = new JsonObject();
            note.addProperty("deckName", deckName);
            note.addProperty("modelName", "Markdown Basic");
            
            JsonObject fields = new JsonObject();
            fields.addProperty("Front", front);
            fields.addProperty("Back", back);
            note.add("fields", fields);
            
            JsonArray tagsArray = new JsonArray();
            tags.forEach(tagsArray::add);
            tagsArray.add("Ideas2Brain");
            note.add("tags", tagsArray);
            
            // Add a unique identifier based on the file path
            JsonObject options = new JsonObject();
            options.addProperty("allowDuplicate", false);
            options.addProperty("duplicateScope", "deck");
            
            JsonArray fieldsArray = new JsonArray();
            fieldsArray.add("Front");
            options.add("duplicateScopeOptions", fieldsArray);
            
            note.add("options", options);
            
            JsonObject params = new JsonObject();
            params.add("note", note);
            
            JsonObject response = makeRequest("addNote", params);
            if (response != null && response.has("result")) {
                return response.get("result").getAsString();
            }
            return null;
        } catch (IOException e) {
            LOG.warn("Failed to add note", e);
            return null;
        }
    }
    
    /**
     * Makes a request to AnkiConnect
     * 
     * @param action The action to perform
     * @param params The parameters for the action
     * @return The response from AnkiConnect
     * @throws IOException If the request fails
     */
    private JsonObject makeRequest(String action, JsonObject params) throws IOException {
        URL url = new URL(ANKI_CONNECT_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        request.addProperty("version", 6);
        if (params != null) {
            request.add("params", params);
        }
        
        String requestBody = gson.toJson(request);
        
        try (var os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        if (connection.getResponseCode() == 200) {
            String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return gson.fromJson(responseBody, JsonObject.class);
        } else {
            LOG.warn("AnkiConnect request failed with status code: " + connection.getResponseCode());
            return null;
        }
    }
}
