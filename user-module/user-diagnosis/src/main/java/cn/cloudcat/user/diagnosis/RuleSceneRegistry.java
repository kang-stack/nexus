package cn.cloudcat.user.diagnosis;

import cn.cloudcat.user.diagnosis.entity.UserRuleScene;
import cn.cloudcat.user.diagnosis.mapper.UserRuleSceneMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则场景注册表：管理 DMN 文件路径的数据库配置与 classpath 默认回退。
 * <p>
 * 优先从 acc_user_rule_scene 表读取路径配置；若未配置则回退到 classpath 预设。
 * 解析结果缓存于内存中。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
@Slf4j
public class RuleSceneRegistry {

    /** classpath 默认场景路径（NETWORK 场景） */
    private static final Map<String, ScenePaths> DEFAULTS = Map.of(
            "NETWORK", new ScenePaths(
                    "dmn/network/network-finding.dmn",
                    "dmn/network/network-priority.dmn",
                    "1.0.0"
            )
    );

    private final UserRuleSceneMapper sceneMapper;
    /** 场景路径缓存：sceneCode → ScenePaths */
    private final Map<String, ScenePaths> cache = new ConcurrentHashMap<>();

    public RuleSceneRegistry(UserRuleSceneMapper sceneMapper) {
        this.sceneMapper = sceneMapper;
    }

    /** 解析场景对应的 DMN 路径，优先 DB 配置，其次 classpath 默认值 */
    public ScenePaths resolve(String sceneCode) {
        return cache.computeIfAbsent(sceneCode, this::load);
    }

    /** 从数据库或默认值加载场景路径配置 */
    private ScenePaths load(String sceneCode) {
        UserRuleScene scene = sceneMapper.selectOne(new LambdaQueryWrapper<UserRuleScene>()
                .eq(UserRuleScene::getSceneCode, sceneCode)
                .eq(UserRuleScene::getEnabled, 1)
                .last("LIMIT 1"));
        if (scene != null) {
            return new ScenePaths(scene.getDmnResourcePath(), scene.getPriorityDmnPath(), "db");
        }
        ScenePaths fallback = DEFAULTS.get(sceneCode);
        if (fallback != null) {
            log.debug("RuleScene {} using classpath defaults", sceneCode);
            return fallback;
        }
        throw new IllegalArgumentException("未知规则场景: " + sceneCode);
    }

    /** DMN 文件路径 + 版本标识 */
    public record ScenePaths(String findingDmnPath, String priorityDmnPath, String versionTag) {
    }
}
