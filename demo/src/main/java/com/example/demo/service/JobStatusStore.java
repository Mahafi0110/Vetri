package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory store for async job status.
 *
 * For production: swap the ConcurrentHashMap for Redis so status
 * survives restarts and works across multiple Render instances.
 *
 * Upgrade path:
 *   @Autowired RedisTemplate<String, JobStatus> redis;
 *   redis.opsForValue().set("job:" + jobId, status, Duration.ofHours(2));
 */
@Component
public class JobStatusStore {

    public enum State { QUEUED, PROCESSING, DONE, FAILED }

    public static class JobStatus {
        public final String jobId;
        public State state;
        public String resultUrl;    // download URL once done
        public String errorMessage; // human-readable on failure
        public final Instant createdAt;
        public Instant updatedAt;

        public JobStatus(String jobId) {
            this.jobId     = jobId;
            this.state     = State.QUEUED;
            this.createdAt = Instant.now();
            this.updatedAt = Instant.now();
        }

        public void transition(State newState) {
            this.state     = newState;
            this.updatedAt = Instant.now();
        }
    }

    // jobId → status; auto-cleaned after 2 hours via cleanOldJobs()
    private final Map<String, JobStatus> store = new ConcurrentHashMap<>();

    /** Create a new job and return its id. */
    public String createJob() {
        String id  = UUID.randomUUID().toString();
        store.put(id, new JobStatus(id));
        return id;
    }

    public JobStatus get(String jobId) {
        return store.get(jobId);
    }

    public void markProcessing(String jobId) {
        update(jobId, j -> j.transition(State.PROCESSING));
    }

    public void markDone(String jobId, String resultUrl) {
        update(jobId, j -> {
            j.resultUrl = resultUrl;
            j.transition(State.DONE);
        });
    }

    public void markFailed(String jobId, String reason) {
        update(jobId, j -> {
            j.errorMessage = reason;
            j.transition(State.FAILED);
        });
    }

    // Evict jobs older than 2 hours — call via @Scheduled if needed
    public void cleanOldJobs() {
        Instant cutoff = Instant.now().minusSeconds(7200);
        store.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
    }

    private void update(String jobId, java.util.function.Consumer<JobStatus> action) {
        JobStatus status = store.get(jobId);
        if (status != null) {
            synchronized (status) { action.accept(status); }
        }
    }
}
