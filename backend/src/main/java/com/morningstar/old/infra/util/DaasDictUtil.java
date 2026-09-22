package com.morningstar.old.infra.util;

import com.morningstar.old.demo.mapper.DaasDictMapper;
import com.morningstar.old.demo.pojo.bo.DaasFieldDictItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DaasDictUtil {
    private final DaasDictMapper daasDictMapper;

    private List<DaasFieldDictItem> getCandidates(String daasId, String daasField){
        Map<String, List<DaasFieldDictItem>> cache = DaasDictCacheHolder.get();
        // 窗口内同 daasId 只查一次库；窗口外（cache 为 null）不缓存直接查
        List<DaasFieldDictItem> daasFieldDictItems = cache == null
                ? daasDictMapper.selectByDaasId(daasId)
                : cache.computeIfAbsent(daasId, daasDictMapper::selectByDaasId);
        return daasFieldDictItems
                .stream()
                .filter(item -> item.getDaasField().equals(daasField))
                .collect(Collectors.toList());
    }

    public String inputValueToCode(List<DaasFieldDictItem> candidates, String daasField, String input){
        List<String> candidateValues = candidates
                .stream()
                .map(DaasFieldDictItem::getLabel)
                .collect(Collectors.toList());
        StringSimilarityUtil.Match match = StringSimilarityUtil.bestMatch(input, candidateValues);
        if(match == null || match.getConfidence() < 0.5){
            throw new RuntimeException(String.format("`%s`字段合法的输入包含：%s", daasField, String.join(",", candidateValues)));
        }
        return candidates.stream()
                .filter(item -> item.getLabel().equals(match.getValue()))
                .findFirst()
                .map(DaasFieldDictItem::getCode)
                .orElse(null);
    }

    public String inputValueToCode(String daasId, String daasField, String input){
        List<DaasFieldDictItem> candidates = getCandidates(daasId, daasField);
        return inputValueToCode(candidates, daasField, input);
    }

    public String outputCodeToValue(List<DaasFieldDictItem> candidates, String output){
        return candidates
                .stream()
                .filter(item -> item.getCode().equals(output))
                .map(DaasFieldDictItem::getLabel)
                .findFirst().orElse(null);
    }

    public String outputCodeToValue(String daasId, String daasField, String output){
        return outputCodeToValue(getCandidates(daasId, daasField), output);
    }
}
