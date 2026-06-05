package cn.cloudcat.intelligent.rag.pipeline;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

import static java.util.Collections.singletonList;

/**
 * 跳过 LLM 改写，原样进入检索（用于 dev 或 LLM 不可达时）。
 */
@Slf4j
public class PassthroughQueryTransformer implements QueryTransformer {

    @Override
    public Collection<Query> transform(Query query) {
        log.info("问题改写已关闭，使用原始问题检索: {}", query.text());
        return singletonList(query);
    }
}
