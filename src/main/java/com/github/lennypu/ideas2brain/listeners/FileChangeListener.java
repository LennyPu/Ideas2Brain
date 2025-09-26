package com.github.lennypu.ideas2brain.listeners;

import com.github.lennypu.ideas2brain.services.AnkiConnectService;
import com.github.lennypu.ideas2brain.services.DatabaseFileStatusService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Listener for file system changes to handle Anki synchronization
 */
public class FileChangeListener implements VirtualFileListener {
    private static final Logger LOG = Logger.getInstance(FileChangeListener.class);
    
    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!isJavaOrKotlinFile(file)) {
            return;
        }
        
        LOG.info("File deleted: " + file.getPath());
        
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);
            String noteId = fileStatusService.getAnkiNoteId(file);
            
            if (noteId != null) {
                // Delete note from Anki
                AnkiConnectService ankiService = ApplicationManager.getApplication().getService(AnkiConnectService.class);
                if (ankiService.isAnkiConnectAvailable()) {
                    boolean deleted = ankiService.deleteNote(noteId);
                    if (deleted) {
                        LOG.info("Deleted Anki note: " + noteId + " for file: " + file.getPath());
                    } else {
                        LOG.warn("Failed to delete Anki note: " + noteId + " for file: " + file.getPath());
                    }
                }
                
                // Remove from database
                fileStatusService.removeFile(file);
            }
        }
    }
    
    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        VirtualFile file = event.getFile();
        if (!isJavaOrKotlinFile(file)) {
            return;
        }
        
        String oldPath = event.getOldParent().getPath() + "/" + file.getName();
        String newPath = file.getPath();
        
        LOG.info("File moved from: " + oldPath + " to: " + newPath);
        
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);
            String noteId = fileStatusService.getAnkiNoteId(file);
            
            if (noteId != null) {
                // Update note in Anki with new deck/tags based on new path
                AnkiConnectService ankiService = ApplicationManager.getApplication().getService(AnkiConnectService.class);
                if (ankiService.isAnkiConnectAvailable()) {
                    // Update note with new deck and tags
                    updateAnkiNoteForMove(ankiService, project, file, noteId);
                }
                
                // Update database with new path
                fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.SYNCED);
            }
        }
    }
    
    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            return;
        }
        
        VirtualFile file = event.getFile();
        if (!isJavaOrKotlinFile(file)) {
            return;
        }
        
        String oldName = (String) event.getOldValue();
        String newName = (String) event.getNewValue();
        
        LOG.info("File renamed from: " + oldName + " to: " + newName);
        
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);
            String noteId = fileStatusService.getAnkiNoteId(file);
            
            if (noteId != null) {
                // Update note in Anki with new name
                AnkiConnectService ankiService = ApplicationManager.getApplication().getService(AnkiConnectService.class);
                if (ankiService.isAnkiConnectAvailable()) {
                    String newClassName = file.getNameWithoutExtension();
                    boolean updated = ankiService.updateNoteFront(noteId, newClassName);
                    if (updated) {
                        LOG.info("Updated Anki note front: " + noteId + " with new name: " + newClassName);
                    } else {
                        LOG.warn("Failed to update Anki note: " + noteId + " with new name: " + newClassName);
                    }
                }
                
                // Update database status
                fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.SYNCED);
            }
        }
    }
    
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!isJavaOrKotlinFile(file)) {
            return;
        }
        
        // Mark file as modified after sync
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);
            DatabaseFileStatusService.FileStatus currentStatus = fileStatusService.getFileStatus(file);
            
            if (currentStatus == DatabaseFileStatusService.FileStatus.SYNCED) {
                fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.MODIFIED_AFTER_SYNC);
                LOG.info("File marked as modified after sync: " + file.getPath());
            }
        }
    }
    
    private boolean isJavaOrKotlinFile(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        
        String extension = file.getExtension();
        return "java".equals(extension) || "kt".equals(extension);
    }
    
    private void updateAnkiNoteForMove(AnkiConnectService ankiService, Project project, VirtualFile file, String noteId) {
        try {
            // Get new deck name based on new path
            String newDeckName = getDeckNameFromFilePath(project, file);
            
            // Get new tags based on new path
            List<String> newTags = getTagsFromFilePath(project, file);
            
            // Update note deck and tags
            boolean updated = ankiService.updateNoteDeckAndTags(noteId, newDeckName, newTags);
            if (updated) {
                LOG.info("Updated Anki note deck and tags for: " + file.getPath());
            } else {
                LOG.warn("Failed to update Anki note deck and tags for: " + file.getPath());
            }
            
        } catch (Exception e) {
            LOG.error("Error updating Anki note for moved file: " + file.getPath(), e);
        }
    }
    
    private String getDeckNameFromFilePath(Project project, VirtualFile file) {
        Path projectPath = Paths.get(project.getBasePath());
        Path filePath = Paths.get(file.getPath());
        
        Path relativePath = projectPath.relativize(filePath.getParent());
        String deckPath = relativePath.toString().replaceAll("/", "::").replace("\\", "::");
        
        return deckPath;
    }
    
    private List<String> getTagsFromFilePath(Project project, VirtualFile file) {
        Path projectPath = Paths.get(project.getBasePath());
        Path filePath = Paths.get(file.getPath());
        
        Path relativePath = projectPath.relativize(filePath);
        
        List<String> tags = new ArrayList<>();
        
        if (relativePath.getParent() != null) {
            for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
                tags.add(relativePath.getName(i).toString());
            }
        }
        
        tags.add(file.getNameWithoutExtension());
        
        return tags;
    }
}