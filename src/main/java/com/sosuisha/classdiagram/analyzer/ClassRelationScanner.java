package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Signature;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sosuisha.classdiagram.DependencyType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * コンパイル済みクラスファイルをリフレクションで分析し、コンポジション・集約・実現関係を返すスキャナー。
 *
 * <p>指定パッケージおよびサブパッケージの {@code .class} ファイルを対象とする。
 * {@link #scan(Path, String)} が {@link ClassRelation} のリストを返す。
 */
public class ClassRelationScanner {

    /**
     * 指定パッケージおよびサブパッケージ内のクラスを分析し、コンポジション・集約・実現関係を返す。
     *
     * @param classRoot コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return 検出された関係のリスト
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     */
    public List<ClassRelation> scan(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var packageDir = classRoot.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }

        var targetClassNames = collectClassNames(classRoot, packageDir);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return analyzeRelations(classRoot, targetClassNames);
    }

    private List<ClassRelation> analyzeRelations(Path classRoot, Set<String> targetClassNames) {
        var relations = new ArrayList<ClassRelation>();

        for (var className : targetClassNames) {
            var classFilePath = classRoot.resolve(className.replace('.', '/') + ".class");
            ClassModel model;
            try {
                model = ClassFile.of().parse(classFilePath);
            } catch (IOException e) {
                continue;
            }

            var constructorParamTypeNames = collectConstructorParamTypeNames(model);

            for (var field : model.fields()) {
                var resolved = resolveFieldTarget(field, targetClassNames);
                if (resolved == null) continue;

                var type = constructorParamTypeNames.contains(resolved.targetClassName())
                    ? DependencyType.AGGREGATION
                    : DependencyType.COMPOSITION;
                relations.add(new ClassRelation(
                    ClassInfo.fromFullyQualifiedName(className),
                    ClassInfo.fromFullyQualifiedName(resolved.targetClassName()),
                    type,
                    resolved.isMany()
                ));
            }

            for (var iface : model.interfaces()) {
                var ifaceName = internalNameToBinary(iface.asInternalName());
                if (targetClassNames.contains(ifaceName)) {
                    relations.add(new ClassRelation(
                        ClassInfo.fromFullyQualifiedName(className),
                        ClassInfo.fromFullyQualifiedName(ifaceName),
                        DependencyType.REALIZATION,
                        false
                    ));
                }
            }
        }

        return List.copyOf(relations);
    }

    private record FieldTarget(String targetClassName, boolean isMany) {}

    private static final Set<String> COLLECTION_TYPES = Set.of(
        "java.util.Collection",
        "java.util.List",
        "java.util.Set",
        "java.util.Queue",
        "java.util.Deque",
        "java.util.ArrayList",
        "java.util.LinkedList",
        "java.util.HashSet",
        "java.util.LinkedHashSet",
        "java.util.TreeSet",
        "java.util.ArrayDeque"
    );

    private static FieldTarget resolveFieldTarget(FieldModel field, Set<String> targetClassNames) {
        var fieldTypeName = classDescToBinaryName(field.fieldTypeSymbol());
        if (targetClassNames.contains(fieldTypeName)) {
            return new FieldTarget(fieldTypeName, false);
        }
        if (COLLECTION_TYPES.contains(fieldTypeName)) {
            var sigAttr = field.findAttribute(Attributes.signature());
            if (sigAttr.isPresent()) {
                var sig = sigAttr.get().asTypeSignature();
                if (sig instanceof Signature.ClassTypeSig classSig
                    && classSig.typeArgs().size() == 1
                    && classSig.typeArgs().get(0) instanceof Signature.TypeArg.Bounded bounded
                    && bounded.wildcardIndicator() == Signature.TypeArg.Bounded.WildcardIndicator.NONE
                    && bounded.boundType() instanceof Signature.ClassTypeSig argSig) {
                    var argName = internalNameToBinary(argSig.className());
                    if (targetClassNames.contains(argName)) {
                        return new FieldTarget(argName, true);
                    }
                }
            }
        }
        return null;
    }

    private static Set<String> collectConstructorParamTypeNames(ClassModel model) {
        var names = new HashSet<String>();
        for (var method : model.methods()) {
            if (!method.methodName().stringValue().equals("<init>")) continue;
            var desc = method.methodTypeSymbol();
            for (int i = 0; i < desc.parameterCount(); i++) {
                names.add(classDescToBinaryName(desc.parameterType(i)));
            }
        }
        return names;
    }

    private static String classDescToBinaryName(ClassDesc desc) {
        if (desc.isPrimitive() || desc.isArray()) {
            return desc.descriptorString();
        }
        var pkg = desc.packageName();
        return pkg.isEmpty() ? desc.displayName() : pkg + "." + desc.displayName();
    }

    private static String internalNameToBinary(String internalName) {
        return internalName.replace('/', '.');
    }

    private static Set<String> collectClassNames(Path classRoot, Path packageDir) {
        var names = new HashSet<String>();
        try (var stream = Files.walk(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .map(p -> toFqn(classRoot.relativize(p)))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }

    private static String toFqn(Path relativePath) {
        var sb = new StringBuilder();
        for (var element : relativePath) {
            if (sb.length() > 0) sb.append('.');
            sb.append(element.toString());
        }
        var s = sb.toString();
        return s.endsWith(".class") ? s.substring(0, s.length() - 6) : s;
    }
}
