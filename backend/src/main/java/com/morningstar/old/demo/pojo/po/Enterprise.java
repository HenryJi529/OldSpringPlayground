package com.morningstar.old.demo.pojo.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.noear.solon.annotation.Param;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "企业信息")
public class Enterprise {

    @Schema(description = "企业id")
    @Param(description = "企业id")
    private Long id;

    @Schema(description = "企业名称")
    @Param(description = "企业名称")
    private String name;

    @Schema(description = "企业具体数据")
    @Param(description = "企业具体数据")
    private String data;

    @Schema(description = "管户账号集合")
    @Param(description = "管户账号集合")
    private Set<String> managers;
}
