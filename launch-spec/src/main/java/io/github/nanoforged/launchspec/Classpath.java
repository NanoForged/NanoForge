package io.github.nanoforged.launchspec;

import java.util.List;

/**
 * 组装完成的 classpath：有序条目列表（core 段在前、game 段在后）。
 *
 * @param entries 有序条目列表；顺序即 -classpath 拼接顺序（冒号分隔）
 */
public record Classpath(List<ClasspathEntry> entries) {

    public Classpath {
        entries = List.copyOf(entries);
    }
}
