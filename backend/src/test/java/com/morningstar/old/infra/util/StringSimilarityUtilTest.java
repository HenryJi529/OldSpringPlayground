package com.morningstar.old.infra.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class StringSimilarityUtilTest {
    @Test
    public void testBestMatch() {
        System.out.println(StringSimilarityUtil.bestMatch("国企", Arrays.asList("国有企业", "央企")));
        System.out.println(StringSimilarityUtil.bestMatch("个体户", Arrays.asList("个体工商户", "央企")));
        System.out.println(StringSimilarityUtil.bestMatch("国家", Arrays.asList("国家机关", "国家机关、党的机关及人民团体")));
        System.out.println(StringSimilarityUtil.bestMatch("无锡小天才公司", Arrays.asList("无锡小天才有限公司", "小天才公司")));
        System.out.println(StringSimilarityUtil.bestMatch("有", Arrays.asList("是", "否")));
    }
}
