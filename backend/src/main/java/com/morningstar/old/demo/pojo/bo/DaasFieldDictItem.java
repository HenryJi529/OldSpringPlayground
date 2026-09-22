package com.morningstar.old.demo.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DaasFieldDictItem {
    private String daasId;

    private String daasField;

    private String dictKey;

    private String code;

    private String label;
}
