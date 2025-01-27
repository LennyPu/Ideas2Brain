package com.github.lennypu.ideas2brain.action;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.lennypu.ideas2brain.utils.JavaDoc2MarkDownUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Ideas2BrainAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        VirtualFile[] files = anActionEvent.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        List<String> markDowns = Arrays.stream(files).map(e -> {
            try {
                return JavaDoc2MarkDownUtil.JavaFileStream2MarkDownString(e.getInputStream());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).toList();

    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return super.getActionUpdateThread();
    }
}
