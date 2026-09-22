package com.morningstar.old.infra.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.util.Comparator;
import java.util.List;

/**
 * 字符串相似度工具
 *
 * <p>注意：相似度只回答"有多像"，不回答"该不该匹配"——置信度阈值（通过/回显/拒识）
 * 由调用方（规整器）判定。</p>
 */
public class StringSimilarityUtil {

    private StringSimilarityUtil() {
    }

    /**
     * 在候选集里找与输入最相似的一项
     */
    public static Match bestMatch(String input, List<String> candidates) {
        if (input == null || input.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .map(candidate -> new Match(candidate,
                        FuzzySearch.tokenSortRatio(input.trim(), candidate.trim()) / 100d))
                .max(Comparator.comparingDouble(Match::getConfidence)).orElse(null);
    }

    /**
     * 一次匹配结果：选中的值 + 置信度
     */
    @Data
    @AllArgsConstructor
    public static class Match {

        /**
         * 选中的候选值
         */
        private String value;

        /**
         * 置信度（相似度得分），[0, 1]
         */
        private double confidence;
    }
}
