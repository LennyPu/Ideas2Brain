package com.github.lennypu.ideas2brain.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Database-backed service for tracking file sync status with Anki
 */
@Service(Service.Level.PROJECT)
public final class DatabaseFileStatusService {
    private static final Logger LOG = Logger.getInstance(DatabaseFileStatusService.class);
    
    private final Project project;
    private Connection connection;
    private final Map<String, FileStatus> cache = new HashMap<>();
    
    public enum FileStatus {
        NOT_SYNCED,
        SYNCED,
        MODIFIED_AFTER_SYNC,
        ERROR
    }
    
    public DatabaseFileStatusService(Project project) {
        this.project = project;
        initializeDatabase();
        loadCache();
    }
    
    public static DatabaseFileStatusService getInstance(Project project) {
        return project.getService(DatabaseFileStatusService.class);
    }
    
    private void initializeDatabase() {
        try {
            String dbPath = project.getBasePath() + "/.idea/ideas2brain.db";
            String url = "jdbc:h2:" + dbPath + ";DB_CLOSE_ON_EXIT=FALSE";
            try {
                Class.forName("org.h2.Driver");
            } catch (ClassNotFoundException e) {
                LOG.error("H2 driver not found", e);
                return;
            }
            connection = DriverManager.getConnection(url, "sa", "");
            
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS file_status (
                    file_path VARCHAR(1000) PRIMARY KEY,
                    status VARCHAR(50) NOT NULL,
                    anki_note_id VARCHAR(100),
                    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }
            
            LOG.info("Database initialized at: " + dbPath);
            
        } catch (SQLException e) {
            LOG.error("Failed to initialize database", e);
        }
    }
    
    private void loadCache() {
        if (connection == null) return;
        
        try {
            String selectSQL = "SELECT file_path, status FROM file_status";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSQL)) {
                
                while (rs.next()) {
                    String filePath = rs.getString("file_path");
                    String statusStr = rs.getString("status");
                    FileStatus status = FileStatus.valueOf(statusStr);
                    cache.put(filePath, status);
                }
            }
            
        } catch (SQLException e) {
            LOG.error("Failed to load cache", e);
        }
    }
    
    public FileStatus getFileStatus(VirtualFile file) {
        if (file == null) {
            return FileStatus.NOT_SYNCED;
        }
        
        String filePath = file.getPath();
        return cache.getOrDefault(filePath, FileStatus.NOT_SYNCED);
    }
    
    public void setFileStatus(VirtualFile file, FileStatus status) {
        if (file == null || connection == null) {
            return;
        }
        
        String filePath = file.getPath();
        
        try {
            String upsertSQL = """
                MERGE INTO file_status (file_path, status, last_modified) 
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
                pstmt.setString(1, filePath);
                pstmt.setString(2, status.name());
                pstmt.executeUpdate();
            }
            
            cache.put(filePath, status);
            
        } catch (SQLException e) {
            LOG.error("Failed to update file status for: " + filePath, e);
        }
    }
    
    public boolean isJavaOrKotlinFile(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        
        String extension = file.getExtension();
        return "java".equals(extension) || "kt".equals(extension);
    }
    
    @Nullable
    public String getAnkiNoteId(VirtualFile file) {
        if (file == null || connection == null) {
            return null;
        }
        
        String filePath = file.getPath();
        
        try {
            String selectSQL = "SELECT anki_note_id FROM file_status WHERE file_path = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setString(1, filePath);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("anki_note_id");
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to get Anki note ID for: " + filePath, e);
        }
        
        return null;
    }
    
    public void markAsSynced(VirtualFile file, String noteId) {
        if (file == null || connection == null) {
            return;
        }
        
        String filePath = file.getPath();
        
        try {
            String upsertSQL = """
                MERGE INTO file_status (file_path, status, anki_note_id, last_modified) 
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
                pstmt.setString(1, filePath);
                pstmt.setString(2, FileStatus.SYNCED.name());
                pstmt.setString(3, noteId);
                pstmt.executeUpdate();
            }
            
            cache.put(filePath, FileStatus.SYNCED);
            
        } catch (SQLException e) {
            LOG.error("Failed to mark file as synced: " + filePath, e);
        }
    }
    
    public void removeFile(VirtualFile file) {
        if (file == null || connection == null) {
            return;
        }
        
        String filePath = file.getPath();
        
        try {
            String deleteSQL = "DELETE FROM file_status WHERE file_path = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
                pstmt.setString(1, filePath);
                pstmt.executeUpdate();
            }
            
            cache.remove(filePath);
            
        } catch (SQLException e) {
            LOG.error("Failed to remove file: " + filePath, e);
        }
    }
    
    public void dispose() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.error("Failed to close database connection", e);
            }
        }
    }
}