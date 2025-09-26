package com.github.lennypu.ideas2brain.action;

import com.github.lennypu.ideas2brain.services.AnkiConnectService;
import com.github.lennypu.ideas2brain.services.DatabaseFileStatusService;
import com.github.lennypu.ideas2brain.utils.JavaDoc2MarkDownUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Action for syncing JavaDoc to Anki
 */
public class SyncToAnkiAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selectedFiles == null || selectedFiles.length == 0) {
            return;
        }

        // Get the AnkiConnectService as an application-level service
        AnkiConnectService ankiConnectService = ApplicationManager.getApplication().getService(AnkiConnectService.class);
        DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);

        // Check if AnkiConnect is available
        if (!ankiConnectService.isAnkiConnectAvailable()) {
            Messages.showErrorDialog(
                    project,
                    "AnkiConnect is not available. Please make sure Anki is running with AnkiConnect plugin installed.",
                    "AnkiConnect Error"
            );
            return;
        }

        int successCount = 0;
        int errorCount = 0;

        for (VirtualFile file : selectedFiles) {
            if (!fileStatusService.isJavaOrKotlinFile(file)) {
                continue;
            }

            try {
                // Convert JavaDoc to Markdown
                String markdown = JavaDoc2MarkDownUtil.JavaFileStream2MarkDownString(file.getInputStream());
                if (markdown == null || markdown.isEmpty()) {
                    fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.ERROR);
                    errorCount++;
                    continue;
                }

                // Get class name for card front
                String className = file.getNameWithoutExtension();

                // Determine deck name based on directory structure
                String deckName = getDeckNameFromFilePath(project, file);

                // Create tags from package structure
                List<String> tags = getTagsFromFilePath(project, file);

                // Add note to Anki
                String noteId = ankiConnectService.addNote(deckName, className, markdown, tags, file.getPath());

                if (noteId != null) {
                    fileStatusService.markAsSynced(file, noteId);
                    successCount++;
                } else {
                    fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.ERROR);
                    errorCount++;
                }
            } catch (IOException ex) {
                fileStatusService.setFileStatus(file, DatabaseFileStatusService.FileStatus.ERROR);
                errorCount++;
            }
        }

        // Show summary message
        if (successCount > 0 || errorCount > 0) {
            String message = String.format(
                    "Sync completed.\nSuccessfully synced: %d\nErrors: %d",
                    successCount,
                    errorCount
            );

            Messages.showInfoMessage(project, message, "Sync to Anki");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        boolean enabled = project != null && selectedFiles != null && selectedFiles.length > 0;

        if (enabled) {
            DatabaseFileStatusService fileStatusService = DatabaseFileStatusService.getInstance(project);
            enabled = Arrays.stream(selectedFiles)
                    .anyMatch(file -> fileStatusService.isJavaOrKotlinFile(file) &&
                            fileStatusService.getFileStatus(file) != DatabaseFileStatusService.FileStatus.SYNCED);
        }

        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Gets the deck name from the file path
     *
     * @param project The project
     * @param file The file
     * @return The deck name
     */
    private String getDeckNameFromFilePath(Project project, VirtualFile file) {
        Path projectPath = Paths.get(project.getBasePath());
        Path filePath = Paths.get(file.getPath());

        // Get relative path from project root
        Path relativePath = projectPath.relativize(filePath.getParent());

        // Convert path separators to "::" for Anki deck hierarchy
        String deckPath = relativePath.toString().replaceAll("/", "::").replace("\\", "::");

        return deckPath;
    }

    /**
     * Gets tags from the file path
     *
     * @param project The project
     * @param file The file
     * @return The tags
     */
    private List<String> getTagsFromFilePath(Project project, VirtualFile file) {
        Path projectPath = Paths.get(project.getBasePath());
        Path filePath = Paths.get(file.getPath());

        // Get relative path from project root
        Path relativePath = projectPath.relativize(filePath);
        
        List<String> tags = new ArrayList<>();
        
        // Add each directory component as a tag
        if (relativePath.getParent() != null) {
            for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
                tags.add(relativePath.getName(i).toString());
            }
        }
        
        // Add file name without extension as a tag
        tags.add(file.getNameWithoutExtension());
        
        return tags;
    }
}
