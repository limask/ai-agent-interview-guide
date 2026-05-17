package com.agent.platform.mapper;

import com.agent.platform.model.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Mapper
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 方法
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
    // 无需写任何代码，MyBatis-Plus 会自动生成 SQL
}
