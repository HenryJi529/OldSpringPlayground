package com.morningstar.old.demo.mapper;

import com.morningstar.old.demo.pojo.bo.DaasFieldDictItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DaasDictMapper {

    /**
     * 按 daas_id 联表查询该接口所有字段绑定的码值列表
     */
    List<DaasFieldDictItem> selectByDaasId(@Param("daasId") String daasId);
}
