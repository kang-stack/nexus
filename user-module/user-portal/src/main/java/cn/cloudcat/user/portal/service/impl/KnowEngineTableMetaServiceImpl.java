package cn.cloudcat.user.portal.service.impl;

import cn.cloudcat.user.portal.entity.TableMeta;
import cn.cloudcat.user.portal.mapper.TableMetaMapper;
import cn.cloudcat.user.portal.service.KnowEngineTableMetaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 知识片段表 Service 实现类
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class KnowEngineTableMetaServiceImpl extends ServiceImpl<TableMetaMapper, TableMeta> implements KnowEngineTableMetaService {
}
