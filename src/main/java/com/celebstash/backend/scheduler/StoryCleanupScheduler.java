package com.celebstash.backend.scheduler;

import com.celebstash.backend.model.Story;
import com.celebstash.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryCleanupScheduler {

    private final StoryRepository storyRepository;

    /**
     * Run every 15 minutes to archive expired 24h stories automatically.
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void archiveExpiredStories() {
        LocalDateTime now = LocalDateTime.now();
        List<Story> activeStories = storyRepository.findAllActiveStories(now);
        
        int archivedCount = 0;
        for (Story story : activeStories) {
            if (story.isExpired()) {
                story.setArchived(true);
                storyRepository.save(story);
                archivedCount++;
            }
        }

        if (archivedCount > 0) {
            log.info("StoryCleanupScheduler: Successfully archived {} expired stories at {}", archivedCount, now);
        }
    }
}
