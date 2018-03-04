package io.github.nzlong.generator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;

import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: nzlong
 * @description:
 * @date: Create in 2018 03 04 下午8:35
 */
public class GenerateAction extends AnAction {


    public GenerateAction() {}

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Editor editor = anActionEvent.getData(PlatformDataKeys.EDITOR);
        this.generationMGS(this.getPsiMethodFromContext(anActionEvent), editor);
    }

    private void generationMGS(PsiClass psiClass, Editor editor) {
        List<PsiField> fields = (new CollectionListModel(psiClass.getFields())).getItems();
        if (fields != null) {
            List<PsiMethod> existsMethod = (new CollectionListModel(psiClass.getMethods())).getItems();
            WriteCommandAction.runWriteCommandAction(psiClass.getProject(), () -> {
                HashSet methodSet = new HashSet();
                existsMethod.stream()
                            .filter(method -> method != null)
                            .forEach(method -> methodSet.add(method.getName()));
                PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
                Supplier<Stream<PsiField>> supplier = () -> fields.stream()
                                                                  .filter(field -> field.getName() != null && field.getName() != "" && !field.getModifierList().hasModifierProperty("final"));
                String emptyStructure = generateEmptyStructure(psiClass);
                PsiMethod emptyStructureMethod = elementFactory.createMethodFromText(emptyStructure, psiClass);
                if (!methodSet.contains(emptyStructureMethod.getName()))
                    psiClass.add(emptyStructureMethod);
                String classStructure = generateClassStructure(psiClass, supplier);
                PsiMethod classStructureMethod = elementFactory.createMethodFromText(classStructure, psiClass);
                if (!methodSet.contains(classStructureMethod.getName()) && classStructureMethod.getParameterList().getParametersCount() > 0)
                    psiClass.add(classStructureMethod);
                String builderStr = generateBuilder(psiClass);
                PsiMethod builder = elementFactory.createMethodFromText(builderStr, psiClass);
                if (!methodSet.contains(builder.getName()))
                    psiClass.add(builder);
                String builderMethod = generateBuilderMethod(psiClass);
                PsiMethod builderStrucuteMethod = elementFactory.createMethodFromText(builderMethod, psiClass);
                if (!methodSet.contains(builderStrucuteMethod.getName()))
                    psiClass.add(builderStrucuteMethod);
                String builderContent = generateBuilderContent(psiClass, supplier);
                PsiClass builderClass = elementFactory.createClassFromText(builderContent, psiClass);
                PsiClass[] innerClasses = psiClass.getInnerClasses();
                List<String> collect = Stream.of(innerClasses)
                                             .filter(clazz -> clazz != null)
                                             .map(clazz -> clazz.getName())
                                             .collect(Collectors.toList());
                if (!collect.contains(toPreUpperCase(psiClass.getName()) + "Builder"))
                    psiClass.add(builderClass);
                supplier.get()
                        .forEach(psiField -> {
                            String getMethodStr = generateGetMethod(psiField);
                            PsiMethod getMethod = elementFactory.createMethodFromText(getMethodStr, psiClass);
                            if (!methodSet.contains(getMethod.getName()))
                                psiClass.add(getMethod);
                            String setMethodStr = generateSetMethod(psiField);
                            PsiMethod setMethod = elementFactory.createMethodFromText(setMethodStr, psiClass);
                            if (!methodSet.contains(setMethod.getName()))
                                psiClass.add(setMethod);
                        });
                String toString = generateToString(psiClass, supplier);
                PsiMethod toStringMethod = elementFactory.createMethodFromText(toString, psiClass);
                if (!methodSet.contains(toStringMethod.getName()))
                    psiClass.add(toStringMethod);
                String text = editor.getDocument().getText();
                text = text.replace("class _Dummy_", "public static class " + toPreUpperCase(psiClass.getName()) + "Builder");
                if (!collect.contains(toPreUpperCase(psiClass.getName()) + "Builder")) {
                    String $text = text.substring(0, text.lastIndexOf("}")).substring(0, text.lastIndexOf("}") - 1);
                    text = $text + "\t}\n\n}";
                }
                PsiDocumentManager.getInstance(psiClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
                editor.getDocument().setText(text);
//                if (editor.getProject() != null) {
//                    PsiDocumentManager.getInstance(editor.getProject()).commitDocument(editor.getDocument());
//                }
            });
        }
    }

    private String generateClassStructure(PsiClass psiClass, Supplier<Stream<PsiField>> supplier) {
        StringBuilder classStructure = new StringBuilder();
        classStructure.append("public ")
                      .append(toPreUpperCase(psiClass.getName()))
                      .append("(");
        supplier.get()
                .forEach(psiField -> classStructure.append(toPreUpperCase(psiField.getType().getPresentableText()) + " " + toPreLowerCase(psiField.getName()) + ", "));
        String $classStructure = classStructure.toString().endsWith(", ") ? classStructure.toString().substring(0, classStructure.lastIndexOf(",")) : classStructure.toString();
        StringBuilder classStructureContent = new StringBuilder();
        classStructureContent.append(") {\n");
        supplier.get()
                .forEach(psiField -> classStructureContent.append("\t\tthis." + toPreLowerCase(psiField.getName()) + " = " + toPreLowerCase(psiField.getName()) + ";\n"));
        classStructureContent.append("\t}");
        return $classStructure + classStructureContent.toString();
    }

    private String generateEmptyStructure(PsiClass psiClass) {
        return "public " + toPreUpperCase(psiClass.getName()) + "() {}";
    }

    private String generateToString(PsiClass psiClass, Supplier<Stream<PsiField>> supplier) {
        StringBuilder toString = new StringBuilder();
        toString.append("@Override\n")
                .append("public String toString() {\n")
                .append("\treturn \"" + toPreUpperCase(psiClass.getName()) + "{");
        supplier.get()
                .forEach(psiField -> toString.append(toPreLowerCase(psiField.getName()) + "=\'\" + " + toPreLowerCase(psiField.getName()) + " + \"\'\" + \n\", "));
        String $toString = toString.toString().endsWith(", ") ? toString.toString().substring(0, toString.toString().lastIndexOf("+") + 1) : toString.toString();
        $toString += "\n\"}\";\n}\n\n";
        return $toString;
    }

    private String generateBuilderContent(PsiClass psiClass, Supplier<Stream<PsiField>> supplier) {
        StringBuilder structure = new StringBuilder();
        String upperBuilderName = toPreUpperCase(psiClass.getName());
        String lowerBuilderName = toPreLowerCase(psiClass.getName());
        structure.append(" \n\tprivate " + upperBuilderName + " " + lowerBuilderName + ";\n\n");
        supplier.get()
                .forEach(psiField -> {
                    String lowerFieldName = toPreLowerCase(psiField.getName());
                    structure.append(" \tpublic " + upperBuilderName + "Builder " + lowerFieldName + "(" + toPreUpperCase(psiField.getType().getPresentableText()) + " " + lowerFieldName + ") {\n");
                    structure.append(" \t" + lowerBuilderName + "." + lowerFieldName + " = " + lowerFieldName + ";\n");
                    structure.append(" \t\t\treturn this;\n\t\t}\n");
                });
        structure.append(" \tpublic " + upperBuilderName + " build() {\n")
                 .append(" \t\treturn this." + lowerBuilderName + ";\n")
                 .append(" \t\t}\n");
        return structure.toString();
    }

    private String generateBuilderMethod(PsiClass psiClass) {
        StringBuilder builder = new StringBuilder();
        String upperBuilderName = toPreUpperCase(psiClass.getName());
        String lowerBuilderName = toPreLowerCase(psiClass.getName());
        builder.append("public static ");
        builder.append(upperBuilderName + "Builder builder(" + upperBuilderName + " " + lowerBuilderName + ") {\n");
        builder.append(" " + upperBuilderName + "Builder " + lowerBuilderName + "Builder = new " + upperBuilderName + "Builder();");
        builder.append(lowerBuilderName + "Builder." + lowerBuilderName + " = " + lowerBuilderName + " == null ? " + "new " + upperBuilderName + "() : " + lowerBuilderName + ";\n");
        builder.append(" return " + lowerBuilderName + "Builder;\n}");
        return builder.toString();
    }

    private String generateBuilder(PsiClass psiClass) {
        StringBuilder builder  = new StringBuilder();
        String upperBuilderName = toPreUpperCase(psiClass.getName());
        String lowerBuilderName = toPreLowerCase(psiClass.getName());
        builder.append("public static ");
        builder.append(upperBuilderName + "Builder builder() {\n");
        builder.append(" " + upperBuilderName + "Builder " + lowerBuilderName + "Builder = new " + upperBuilderName + "Builder();");
        builder.append(lowerBuilderName + "Builder." + lowerBuilderName + " = new " + upperBuilderName + "();");
        builder.append(" return " + lowerBuilderName + "Builder;\n}\n\n");
        return builder.toString();
    }

    private String generateSetMethod(PsiField psiField) {
        StringBuilder setMethod = new StringBuilder();
        setMethod.append("public ");
        if (psiField.getModifierList().hasModifierProperty("static")) {
            setMethod.append("static ");
        }

        setMethod.append("void ");
        if (psiField.getType().getPresentableText().equals("boolean")) {
            setMethod.append("is");
        } else {
            setMethod.append("set");
        }

        setMethod.append(toPreUpperCase(psiField.getName()));
        String field = toPreLowerCase(psiField.getName());
        setMethod.append("(" + psiField.getType().getPresentableText() + " " + field + ") {\n");
        setMethod.append(" this." + field + " = " + field + ";\n}\n");
        return setMethod.toString();
    }

    private String generateGetMethod(PsiField psiField) {
        StringBuilder getMethod = new StringBuilder();
        getMethod.append("public ");
        if (psiField.getModifierList().hasModifierProperty("static")) {
            getMethod.append("static ");
        }

        getMethod.append(psiField.getType().getPresentableText() + " ");
        if (psiField.getType().getPresentableText().equals("boolean")) {
            getMethod.append("is");
        } else {
            getMethod.append("get");
        }

        getMethod.append(toPreUpperCase(psiField.getName()));
        getMethod.append("() {\n");
        getMethod.append(" return this." + toPreLowerCase(psiField.getName()) + ";\n}");
        return getMethod.toString();
    }

    private String toPreUpperCase(String str) {
        if (str != null && str != "") {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        return "";
    }

    private String toPreLowerCase(String str) {
        if (str != null && str != "") {
            return str.substring(0, 1).toLowerCase() + str.substring(1);
        }
        return "";
    }

    private PsiClass getPsiMethodFromContext(AnActionEvent e) {
        PsiElement elementAt = this.getPsiElement(e);
        return elementAt == null ? null : PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile != null && editor != null) {
            int offset = editor.getCaretModel().getOffset();
            return psiFile.findElementAt(offset);
        } else {
            e.getPresentation().setEnabled(false);
            return null;
        }
    }

}
