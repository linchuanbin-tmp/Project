package com.agent.code.config;

import com.agent.code.service.MetadataCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Metadata cache manager.
 * <p>
 * - Loads MySQL table schemas into Redis on startup.
 * - Auto-refreshes the cache every 30 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataCacheManager {

    private final MetadataCacheService metadataCacheService;

    /**
     * Warm up the metadata cache after application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Code Agent 启动完成，开始预热元数据缓存...");
        try {
            var metadata = metadataCacheService.refreshAllMetadata();
            log.info("✅ 缓存预热完成！已加载 {} 张表的元数据", metadata.size());
        } catch (Exception e) {
            log.warn("⚠️ 缓存预热失败（可能是数据库未就绪）: {}", e.getMessage());
            log.warn("⚠️ 数据库就绪后，请调用 POST /api/code/metadata/refresh 手动刷新");
        }
    }

    /**
     * Scheduled cache refresh (every 30 minutes).
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledRefresh() {
        log.info("⏰ 定时刷新元数据缓存...");
        try {
            metadataCacheService.refreshAllMetadata();
        } catch (Exception e) {
            log.error("❌ 定时刷新失败: {}", e.getMessage());
        }
    }
}
