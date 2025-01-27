package com.github.lennypu.ideas2brain.utils;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.utils.PositionUtils.sortByBeginPosition;

public class JavaDoc2MarkDownUtil {

    public static String JavaFileStream2MarkDownString(InputStream inputStream) {
        CompilationUnit unit = StaticJavaParser.parse(inputStream);
        StringBuilder stringBuilder = new StringBuilder("");
        new VoidVisitorAdapter<StringBuilder>(){
            int currentLevel = 1;  // 当前的层级，初始为1

            @Override
            public void visit(ClassOrInterfaceDeclaration n, StringBuilder arg) {
                extractComments(n, n.getComment(), n.getNameAsString(), stringBuilder, currentLevel);

                currentLevel++;
                super.visit(n, arg);
                currentLevel--;
            }

            @Override
            public void visit(FieldDeclaration n, StringBuilder arg) {
                extractComments(n, n.getComment(), n.getVariables().get(0).getNameAsString(), stringBuilder, currentLevel);
                currentLevel++;
                super.visit(n, arg);
                currentLevel--;
            }
            @Override
            public void visit(MethodDeclaration n, StringBuilder arg) {
                extractComments(n, n.getComment(), n.getNameAsString(), stringBuilder, currentLevel);
                currentLevel++;
                super.visit(n, arg);
                currentLevel--;
            }
        }.visit(unit, stringBuilder);


        return stringBuilder.toString();
    }


    private static void extractComments(Node n, Optional<Comment> commentOptional, String nameAsString, StringBuilder stringBuilder, int currentLevel) {
        if (currentLevel >= 7){
            throw new RuntimeException("The depth of Markdown headings cannot exceed 6");
        }

        if ("Test_A_A".equals(nameAsString)) {
            System.out.println("");
        }
        if (commentOptional.isPresent()) {
            List<Comment> result = new ArrayList<>();
            JavaDoc2MarkDownUtil.getOrphanCommentsBeforeThisChildNode(n, result);
            stringBuilder.append("#".repeat(currentLevel)).append(" ").append(nameAsString).append(System.lineSeparator());
            result.add(commentOptional.get());
            result.stream().map(e -> {
                String content = e.getContent();
                if (e.getContent().startsWith("/")) {
                    return content.substring(1).trim();
                }
                return content.trim();
            }).filter(e -> !"".equals(e)).forEach(e -> stringBuilder.append(e.trim()).append(System.lineSeparator()));
        }
    }


    /**
     *
     * From https://stackoverflow.com/questions/61009945/javaparser-collect-multiple-orphan-and-not-attached-comments-from-inner-nested-c
     * From https://github.com/randoop/randoop/blob/41adecbe9f098cfac772f342a62e669246aae69a/src/main/java/randoop/main/Minimize.java#L1454-L1501
     * This is stolen from JavaParser's PrettyPrintVisitor.printOrphanCommentsBeforeThisChildNode,
     * with light modifications.
     *
     * @param node the node whose orphan comments to collect
     * @param result the list to add orphan comments to. Is side-effected by this method. The
     *     implementation uses this to minimize the diffs against upstream.
     */
    @SuppressWarnings({
            "JdkObsolete", // for LinkedList
            "ReferenceEquality"
    })
    private static void getOrphanCommentsBeforeThisChildNode(final Node node, List<Comment> result) {
        if (node instanceof Comment) {
            return;
        }

        Node parent = node.getParentNode().orElse(null);
        if (parent == null) {
            return;
        }
        List<Node> everything = new LinkedList<>(parent.getChildNodes());
        sortByBeginPosition(everything);
        int positionOfTheChild = -1;
        for (int i = 0; i < everything.size(); i++) {
            if (everything.get(i) == node) positionOfTheChild = i;
        }
        if (positionOfTheChild == -1) {
            throw new AssertionError("I am not a child of my parent.");
        }
        int positionOfPreviousChild = -1;
        for (int i = positionOfTheChild - 1; i >= 0 && positionOfPreviousChild == -1; i--) {
            if (!(everything.get(i) instanceof Comment)) positionOfPreviousChild = i;
        }
        for (int i = positionOfPreviousChild + 1; i < positionOfTheChild; i++) {
            Node nodeToPrint = everything.get(i);
            if (!(nodeToPrint instanceof Comment))
                throw new RuntimeException(
                        "Expected comment, instead "
                                + nodeToPrint.getClass()
                                + ". Position of previous child: "
                                + positionOfPreviousChild
                                + ", position of child "
                                + positionOfTheChild);
            result.add((Comment) nodeToPrint);
        }
    }
}
