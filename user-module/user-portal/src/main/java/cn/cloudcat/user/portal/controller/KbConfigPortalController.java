package cn.cloudcat.user.portal.controller;

import cn.cloudcat.user.common.entity.KbChitchatCategory;
import cn.cloudcat.user.common.entity.KbChitchatPattern;
import cn.cloudcat.user.common.entity.KbChitchatReply;
import cn.cloudcat.user.common.entity.KbFaqAlias;
import cn.cloudcat.user.common.entity.KbHumanRoute;
import cn.cloudcat.user.common.entity.KbSceneFaq;
import cn.cloudcat.user.common.entity.KbSessionTemplate;
import cn.cloudcat.user.common.entity.KbUtteranceConnector;
import cn.cloudcat.user.common.mapper.KbChitchatCategoryMapper;
import cn.cloudcat.user.common.mapper.KbChitchatPatternMapper;
import cn.cloudcat.user.common.mapper.KbChitchatReplyMapper;
import cn.cloudcat.user.common.mapper.KbFaqAliasMapper;
import cn.cloudcat.user.common.mapper.KbSessionTemplateMapper;
import cn.cloudcat.user.common.mapper.KbUtteranceConnectorMapper;
import cn.cloudcat.user.diagnosis.entity.UserRuleScene;
import cn.cloudcat.user.diagnosis.entity.UserRuleVersion;
import cn.cloudcat.user.diagnosis.mapper.UserRuleSceneMapper;
import cn.cloudcat.user.diagnosis.mapper.UserRuleVersionMapper;
import cn.cloudcat.user.knowledge.chitchat.ChitchatInterceptService;
import cn.cloudcat.user.knowledge.entity.UserActionTemplate;
import cn.cloudcat.user.knowledge.engine.FaqAliasMatchService;
import cn.cloudcat.user.knowledge.service.UserActionTemplateService;
import cn.cloudcat.user.knowledge.service.KbHumanRouteService;
import cn.cloudcat.user.knowledge.service.KbSceneFaqService;
import cn.cloudcat.user.knowledge.utterance.UtteranceConnectorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * V2 知识运营：DMN 场景 / 寒暄 / 会话模板。
 *
 * @author Kang
 * @since 2026-06-02
 */
@RestController
@RequestMapping("/api/cs/v1/admin/kb-config")
@RequiredArgsConstructor
public class KbConfigPortalController {

    private final UserRuleSceneMapper ruleSceneMapper;
    private final UserRuleVersionMapper ruleVersionMapper;
    private final KbChitchatCategoryMapper chitchatCategoryMapper;
    private final KbChitchatPatternMapper chitchatPatternMapper;
    private final KbChitchatReplyMapper chitchatReplyMapper;
    private final KbSessionTemplateMapper sessionTemplateMapper;
    private final ChitchatInterceptService chitchatInterceptService;
    private final KbHumanRouteService humanRouteService;
    private final KbFaqAliasMapper faqAliasMapper;
    private final FaqAliasMatchService faqAliasMatchService;
    private final UserActionTemplateService actionTemplateService;
    private final KbSceneFaqService sceneFaqService;
    private final KbUtteranceConnectorMapper utteranceConnectorMapper;
    private final UtteranceConnectorService utteranceConnectorService;

    /** GET /api/admin/kb-config/rule-scenes — 查询所有规则场景 */
    @GetMapping("/rule-scenes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<UserRuleScene> listRuleScenes() {
        return ruleSceneMapper.selectList(new LambdaQueryWrapper<UserRuleScene>()
                .orderByAsc(UserRuleScene::getSceneCode));
    }

    /** PUT /api/admin/kb-config/rule-scenes/{id} — 更新规则场景 */
    @PutMapping("/rule-scenes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public UserRuleScene updateRuleScene(@PathVariable Long id, @RequestBody UserRuleScene update) {
        update.setId(id);
        ruleSceneMapper.updateById(update);
        return ruleSceneMapper.selectById(id);
    }

    /** GET /api/admin/kb-config/rule-versions — 查询规则版本（可按场景过滤） */
    @GetMapping("/rule-versions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<UserRuleVersion> listRuleVersions(@RequestParam(required = false) String sceneCode) {
        LambdaQueryWrapper<UserRuleVersion> q = new LambdaQueryWrapper<UserRuleVersion>()
                .orderByDesc(UserRuleVersion::getPublishedAt);
        if (sceneCode != null && !sceneCode.isBlank()) {
            q.eq(UserRuleVersion::getSceneCode, sceneCode);
        }
        return ruleVersionMapper.selectList(q);
    }

    /** GET /api/admin/kb-config/chitchat/categories — 查询寒暄类别 */
    @GetMapping("/chitchat/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbChitchatCategory> listChitchatCategories() {
        return chitchatCategoryMapper.selectList(new LambdaQueryWrapper<KbChitchatCategory>()
                .orderByAsc(KbChitchatCategory::getCategoryCode));
    }

    /** GET /api/admin/kb-config/chitchat/patterns — 查询寒暄匹配模式 */
    @GetMapping("/chitchat/patterns")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbChitchatPattern> listChitchatPatterns() {
        return chitchatPatternMapper.selectList(new LambdaQueryWrapper<KbChitchatPattern>()
                .orderByAsc(KbChitchatPattern::getCategoryCode));
    }

    /** PUT /api/admin/kb-config/chitchat/patterns/{id} — 更新寒暄匹配模式并重载缓存 */
    @PutMapping("/chitchat/patterns/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbChitchatPattern updateChitchatPattern(@PathVariable Long id, @RequestBody KbChitchatPattern update) {
        update.setId(id);
        chitchatPatternMapper.updateById(update);
        chitchatInterceptService.reloadPatterns();
        return chitchatPatternMapper.selectById(id);
    }

    /** GET /api/admin/kb-config/chitchat/replies — 查询寒暄回复 */
    @GetMapping("/chitchat/replies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbChitchatReply> listChitchatReplies() {
        return chitchatReplyMapper.selectList(new LambdaQueryWrapper<KbChitchatReply>()
                .orderByAsc(KbChitchatReply::getCategoryCode));
    }

    /** PUT /api/admin/kb-config/chitchat/replies/{id} — 更新寒暄回复 */
    @PutMapping("/chitchat/replies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbChitchatReply updateChitchatReply(@PathVariable Long id, @RequestBody KbChitchatReply update) {
        update.setId(id);
        chitchatReplyMapper.updateById(update);
        return chitchatReplyMapper.selectById(id);
    }

    /** POST /api/admin/kb-config/chitchat/reload — 手动重载寒暄缓存 */
    @PostMapping("/chitchat/reload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> reloadChitchat() {
        chitchatInterceptService.reloadPatterns();
        return Map.of("success", true);
    }

    /** GET /api/admin/kb-config/session-templates — 查询会话模板 */
    @GetMapping("/session-templates")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbSessionTemplate> listSessionTemplates() {
        return sessionTemplateMapper.selectList(new LambdaQueryWrapper<KbSessionTemplate>()
                .orderByAsc(KbSessionTemplate::getTemplateCode));
    }

    /** PUT /api/admin/kb-config/session-templates/{id} — 更新会话模板 */
    @PutMapping("/session-templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbSessionTemplate updateSessionTemplate(@PathVariable Long id, @RequestBody KbSessionTemplate update) {
        update.setId(id);
        sessionTemplateMapper.updateById(update);
        return sessionTemplateMapper.selectById(id);
    }

    /** GET /api/admin/kb-config/quick-entries — 查询快捷入口 */
    @GetMapping("/quick-entries")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbSceneFaq> listQuickEntries() {
        return sceneFaqService.listAll();
    }

    /** POST /api/admin/kb-config/quick-entries — 创建快捷入口 */
    @PostMapping("/quick-entries")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KbSceneFaq createQuickEntry(@RequestBody KbSceneFaq body) {
        return sceneFaqService.create(body);
    }

    /** PUT /api/admin/kb-config/quick-entries/{id} — 更新快捷入口 */
    @PutMapping("/quick-entries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbSceneFaq updateQuickEntry(@PathVariable Long id, @RequestBody KbSceneFaq update) {
        return sceneFaqService.update(id, update);
    }

    /** GET /api/admin/kb-config/human-routes — 查询人工路由 */
    @GetMapping("/human-routes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbHumanRoute> listHumanRoutes() {
        return humanRouteService.listAll();
    }

    /** PUT /api/admin/kb-config/human-routes/{id} — 更新人工路由 */
    @PutMapping("/human-routes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbHumanRoute updateHumanRoute(@PathVariable Long id, @RequestBody KbHumanRoute update) {
        return humanRouteService.update(id, update);
    }

    /** GET /api/admin/kb-config/faq-aliases — 查询FAQ别名 */
    @GetMapping("/faq-aliases")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbFaqAlias> listFaqAliases() {
        return faqAliasMapper.selectList(new LambdaQueryWrapper<KbFaqAlias>()
                .orderByAsc(KbFaqAlias::getId));
    }

    /** PUT /api/admin/kb-config/faq-aliases/{id} — 更新FAQ别名并重载缓存 */
    @PutMapping("/faq-aliases/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbFaqAlias updateFaqAlias(@PathVariable Long id, @RequestBody KbFaqAlias update) {
        update.setId(id);
        faqAliasMapper.updateById(update);
        faqAliasMatchService.reload();
        return faqAliasMapper.selectById(id);
    }

    /** POST /api/admin/kb-config/faq-aliases/reload — 手动重载FAQ别名缓存 */
    @PostMapping("/faq-aliases/reload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> reloadFaqAliases() {
        faqAliasMatchService.reload();
        return Map.of("success", true);
    }

    /** GET /api/admin/kb-config/action-templates — 查询动作模板 */
    @GetMapping("/action-templates")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<UserActionTemplate> listActionTemplates() {
        return actionTemplateService.listAll();
    }

    /** PUT /api/admin/kb-config/action-templates/{id} — 更新动作模板 */
    @PutMapping("/action-templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public UserActionTemplate updateActionTemplate(@PathVariable Long id, @RequestBody UserActionTemplate update) {
        return actionTemplateService.update(id, update);
    }

    /** POST /api/admin/kb-config/action-templates/reload — 手动重载动作模板缓存 */
    @PostMapping("/action-templates/reload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> reloadActionTemplates() {
        actionTemplateService.reload();
        return Map.of("success", true);
    }

    /** GET /api/admin/kb-config/utterance-connectors — 分句连接词 */
    @GetMapping("/utterance-connectors")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<KbUtteranceConnector> listUtteranceConnectors() {
        return utteranceConnectorMapper.selectList(new LambdaQueryWrapper<KbUtteranceConnector>()
                .orderByAsc(KbUtteranceConnector::getSortOrder)
                .orderByAsc(KbUtteranceConnector::getId));
    }

    /** POST /api/admin/kb-config/utterance-connectors — 新增连接词 */
    @PostMapping("/utterance-connectors")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public KbUtteranceConnector createUtteranceConnector(@RequestBody KbUtteranceConnector body) {
        utteranceConnectorMapper.insert(body);
        utteranceConnectorService.reload();
        return body;
    }

    /** PUT /api/admin/kb-config/utterance-connectors/{id} */
    @PutMapping("/utterance-connectors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public KbUtteranceConnector updateUtteranceConnector(@PathVariable Long id,
                                                         @RequestBody KbUtteranceConnector update) {
        update.setId(id);
        utteranceConnectorMapper.updateById(update);
        utteranceConnectorService.reload();
        return utteranceConnectorMapper.selectById(id);
    }

    /** POST /api/admin/kb-config/utterance-connectors/reload */
    @PostMapping("/utterance-connectors/reload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> reloadUtteranceConnectors() {
        utteranceConnectorService.reload();
        return Map.of("success", true);
    }
}
