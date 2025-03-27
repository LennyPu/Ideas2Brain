package com.github.lennypu.ideas2brain.services;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for tracking file sync status with Anki
 */
@Service(Service.Level.PROJECT)
@State(
    name = "Ideas2BrainFileStatus",
    storages = @Storage("ideas2brain-file-status.xml")
)
public final class FileStatusService implements PersistentStateComponent<FileStatusService.State> {
    private State myState = new State();
    
    public static FileStatusService getInstance(Project project) {
        return project.getService(FileStatusService.class);
    }
    
    public static class State {
        public Map<String, FileStatus> fileStatuses = new HashMap<>();
    }
    
    public enum FileStatus {
        NOT_SYNCED,
        SYNCED,
        MODIFIED_AFTER_SYNC,
        ERROR
    }
    
    @Override
    public @Nullable State getState() {
        return myState;
    }
    
    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }
    
    /**
     * Gets the status of a file
     * 
     * @param file The file to check
     * @return The status of the file
     */
    public FileStatus getFileStatus(VirtualFile file) {
        if (file == null) {
            return FileStatus.NOT_SYNCED;
        }
        
        String filePath = file.getPath();
        return myState.fileStatuses.getOrDefault(filePath, FileStatus.NOT_SYNCED);
    }
    
    /**
     * Sets the status of a file
     * 
     * @param file The file to update
     * @param status The new status
     */
    public void setFileStatus(VirtualFile file, FileStatus status) {
        if (file == null) {
            return;
        }
        
        String filePath = file.getPath();
        myState.fileStatuses.put(filePath, status);
    }
    
    /**
     * Checks if a file is a Java or Kotlin file
     * 
     * @param file The file to check
     * @return true if the file is a Java or Kotlin file
     */
    public boolean isJavaOrKotlinFile(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        
        String extension = file.getExtension();
        return "java".equals(extension) || "kt".equals(extension);
    }
    
    /**
     * Gets the Anki note ID for a file
     * 
     * @param file The file to check
     * @return The Anki note ID, or null if not synced
     */
    public String getAnkiNoteId(VirtualFile file) {
        if (file == null) {
            return null;
        }
        
        String filePath = file.getPath();
        // In a real implementation, you might want to store note IDs separately
        // For simplicity, we're just checking if the file is synced
        return myState.fileStatuses.getOrDefault(filePath, FileStatus.NOT_SYNCED) == FileStatus.SYNCED ? 
               filePath.hashCode() + "" : null;
    }
    
    /**
     * Marks a file as synced with Anki
     * 
     * @param file The file to mark
     * @param noteId The Anki note ID
     */
    public void markAsSynced(VirtualFile file, String noteId) {
        if (file == null) {
            return;
        }
        
        setFileStatus(file, FileStatus.SYNCED);
    }
}
