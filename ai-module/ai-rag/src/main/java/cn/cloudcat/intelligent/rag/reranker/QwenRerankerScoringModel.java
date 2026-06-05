package cn.cloudcat.intelligent.rag.reranker;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Qwen3-rerank 远程重排序模型（DashScope API）
 * <p>
 * 通过 DashScope 原生 rerank API 对文档进行重排序评分，
 * 文档量较小时延迟优于本地 ONNX 模型（无冷启动开销）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
public class QwenRerankerScoringModel implements ScoringModel {

    private static final String RERANK_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String modelName;
    private final int topN;

    public QwenRerankerScoringModel(String apiKey, String modelName) {
        this(apiKey, modelName, 10);
    }

    public QwenRerankerScoringModel(String apiKey, String modelName, int topN) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.topN = topN;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(new ArrayList<>());
        }

        List<String> documents = segments.stream()
                .map(TextSegment::text)
                .toList();

        JSONObject body = JSONObject.of(
                "model", modelName,
                "input", JSONObject.of(
                        "query", query,
                        "documents", documents
                ),
                "parameters", JSONObject.of(
                        "top_n", Math.min(topN, documents.size()),
                        "return_documents", false
                )
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(RERANK_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> httpResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                log.error("Qwen3-rerank API 返回非200状态码: {}, body={}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException("Qwen3-rerank API error: " + httpResponse.body());
            }

            JSONObject respBody = JSON.parseObject(httpResponse.body());
            List<JSONObject> results = respBody.getJSONObject("output").getList("results", JSONObject.class);

            // 将结果按 index 映射回原始顺序
            double[] scores = new double[segments.size()];
            for (JSONObject result : results) {
                int idx = result.getIntValue("index");
                double score = result.getDoubleValue("relevance_score");
                if (idx >= 0 && idx < scores.length) {
                    scores[idx] = score;
                }
            }

            List<Double> scoreList = new ArrayList<>(scores.length);
            for (double s : scores) {
                scoreList.add(s);
            }

            log.debug("Qwen3-rerank 完成: query={}, documents={}, results={}", query, documents.size(), results.size());
            return Response.from(scoreList);
        } catch (Exception e) {
            log.error("Qwen3-rerank API 调用失败: query={}", query, e);
            throw new RuntimeException("Qwen3-rerank API 调用失败", e);
        }
    }

    @Override
    public Response<Double> score(String text, String query) {
        List<Double> scores = scoreAll(
                List.of(TextSegment.from(text)),
                query
        ).content();
        return Response.from(scores.isEmpty() ? 0.0 : scores.get(0));
    }

    @Override
    public Response<Double> score(TextSegment segment, String query) {
        return score(segment.text(), query);
    }
}
