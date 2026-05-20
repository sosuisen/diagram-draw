package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassRelationScanner {

    /**
     * 指定パッケージ内のクラスを分析し、コンポジション・集約関係を返す。
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

        var targetClassNames = collectClassNames(packageDir, packageName);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return analyzeRelations(classRoot, targetClassNames);
    }

    private List<ClassRelation> analyzeRelations(Path classRoot, Set<String> targetClassNames) {
        var relations = new ArrayList<ClassRelation>();

        try (var loader = new URLClassLoader(new java.net.URL[]{toUrl(classRoot)}, getClass().getClassLoader())) {
            for (var className : targetClassNames) {
                var clazz = loadClass(className, loader);
                if (clazz == null) continue;

                var constructorParamTypeNames = collectConstructorParamTypeNames(clazz);

                for (var field : clazz.getDeclaredFields()) {
                    var fieldTypeName = field.getType().getName();
                    if (!targetClassNames.contains(fieldTypeName)) continue;

                    var type = constructorParamTypeNames.contains(fieldTypeName)
                        ? RelationType.AGGREGATION
                        : RelationType.COMPOSITION;
                    relations.add(new ClassRelation(className, fieldTypeName, type, false));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return List.copyOf(relations);
    }

    private static Set<String> collectConstructorParamTypeNames(Class<?> clazz) {
        var names = new HashSet<String>();
        for (var constructor : clazz.getDeclaredConstructors()) {
            for (var paramType : constructor.getParameterTypes()) {
                names.add(paramType.getName());
            }
        }
        return names;
    }

    private static Class<?> loadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static java.net.URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    private static Set<String> collectClassNames(Path packageDir, String packageName) {
        var names = new HashSet<String>();
        try (var stream = Files.list(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .map(p -> p.getFileName().toString())
                .filter(name -> !name.contains("$"))
                .map(name -> packageName + "." + name.replace(".class", ""))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
