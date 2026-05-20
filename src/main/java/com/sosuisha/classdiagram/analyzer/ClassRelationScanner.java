package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        return List.of();
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
