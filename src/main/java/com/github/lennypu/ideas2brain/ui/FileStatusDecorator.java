package com.github.lennypu.ideas2brain.ui;

import com.github.lennypu.ideas2brain.services.FileStatusService;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Decorator for showing file sync status in Project View
 */
public class FileStatusDecorator implements ProjectViewNodeDecorator {

    // Load the icons
    private static final Icon CLASS_ICON = IconLoader.getIcon("/icons/class/class.svg", FileStatusDecorator.class);
    private static final Icon INTERFACE_ICON = IconLoader.getIcon("/icons/interface/interface.svg", FileStatusDecorator.class);
    private static final Icon ENUM_ICON = IconLoader.getIcon("/icons/enum/enum.svg", FileStatusDecorator.class);
    private static final Icon ABSTRACT_CLASS_ICON = IconLoader.getIcon("/icons/classAbstract/classAbstract.svg", FileStatusDecorator.class);

    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        VirtualFile file = node.getVirtualFile();
        if (file == null || file.isDirectory()) {
            return;
        }

        Project project = node.getProject();
        if (project == null) {
            return;
        }

        FileStatusService fileStatusService = FileStatusService.getInstance(project);
        if (!fileStatusService.isJavaOrKotlinFile(file)) {
            return;
        }

        FileStatusService.FileStatus status = fileStatusService.getFileStatus(file);

        // Only change the icon if the file has been synced and it's a Java file
        if (status == FileStatusService.FileStatus.SYNCED && "java".equals(file.getExtension())) {
            // Get the appropriate icon based on the Java file type
            Icon icon = getSyncedIconForJavaFile(file);
            if (icon != null) {
                data.setIcon(icon);
            }
        }

        // Add status text to the presentation without changing text attributes
        // This preserves any formatting applied by other plugins like Git
        String currentName = data.getPresentableText();
        if (currentName == null) {
            currentName = file.getNameWithoutExtension();
        }
        
        switch (status) {
            case SYNCED:
                data.setPresentableText(currentName + " [Synced]");
                break;
            case MODIFIED_AFTER_SYNC:
                data.setPresentableText(currentName + " [Modified]");
                break;
            case ERROR:
                data.setPresentableText(currentName + " [Error]");
                break;
            case NOT_SYNCED:
                // No decoration for NOT_SYNCED
                break;
        }
    }

    /**
     * Gets the appropriate synced icon for a Java file based on its content
     * 
     * @param file The Java file
     * @return The appropriate icon, or null if no icon is available
     */
    private Icon getSyncedIconForJavaFile(VirtualFile file) {
        try {
            // Simple content-based detection
            String content = readFileContent(file);
            
            // Check for interface
            if (content.contains("interface " + file.getNameWithoutExtension())) {
                return INTERFACE_ICON;
            }
            
            // Check for enum
            if (content.contains("enum " + file.getNameWithoutExtension())) {
                return ENUM_ICON;
            }
            
            // Check for abstract class
            if (content.contains("abstract class " + file.getNameWithoutExtension())) {
                return ABSTRACT_CLASS_ICON;
            }
            
            // Default to class icon
            if (content.contains("class " + file.getNameWithoutExtension())) {
                return CLASS_ICON;
            }
            
            return null;
        } catch (Exception e) {
            // If anything goes wrong, fall back to the default synced icon
            return null;
        }
    }
    
    /**
     * Reads the content of a file
     * 
     * @param file The file to read
     * @return The content of the file
     * @throws IOException If an I/O error occurs
     */
    private String readFileContent(VirtualFile file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
