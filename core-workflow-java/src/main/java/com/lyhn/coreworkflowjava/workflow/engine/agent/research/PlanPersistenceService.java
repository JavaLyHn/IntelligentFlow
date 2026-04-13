package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class PlanPersistenceService {

    private static final String PLAN_FILE_PREFIX = "plan-";
    private static final String PLAN_FILE_SUFFIX = ".md";

    private final Path storagePath;
    private final PlanMarkdownSerializer serializer;

    public PlanPersistenceService(String storagePath) {
        this.storagePath = Paths.get(storagePath);
        this.serializer = new PlanMarkdownSerializer();
        ensureStorageDirectory();
    }

    public PlanPersistenceService(Path storagePath) {
        this.storagePath = storagePath;
        this.serializer = new PlanMarkdownSerializer();
        ensureStorageDirectory();
    }

    public String savePlan(ResearchPlan plan) {
        String planId = generatePlanId();
        savePlan(plan, planId);
        return planId;
    }

    public String savePlan(ResearchPlan plan, String planId) {
        log.info("[PlanPersistenceService] Saving plan: planId={}, query={}", planId, plan.getQuery());

        try {
            String markdown = serializer.toMarkdown(plan, planId);
            Path filePath = resolvePlanFile(planId);
            Files.writeString(filePath, markdown, StandardCharsets.UTF_8);
            log.info("[PlanPersistenceService] Plan saved to: {}", filePath);
            return planId;
        } catch (IOException e) {
            log.error("[PlanPersistenceService] Failed to save plan: {}", e.getMessage(), e);
            throw new PlanPersistenceException("Failed to save plan: " + planId, e);
        }
    }

    public ResearchPlan loadPlan(String planId) {
        log.info("[PlanPersistenceService] Loading plan: planId={}", planId);

        try {
            Path filePath = resolvePlanFile(planId);
            if (!Files.exists(filePath)) {
                log.warn("[PlanPersistenceService] Plan file not found: {}", filePath);
                return null;
            }

            String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
            ResearchPlan plan = serializer.fromMarkdown(markdown);

            if (plan != null) {
                log.info("[PlanPersistenceService] Plan loaded: steps={}, currentStep={}",
                        plan.getStepCount(), plan.getCurrentStepIndex());
            }

            return plan;
        } catch (IOException e) {
            log.error("[PlanPersistenceService] Failed to load plan: {}", e.getMessage(), e);
            throw new PlanPersistenceException("Failed to load plan: " + planId, e);
        }
    }

    public String loadPlanContext(String planId, int fromStepIndex) {
        log.info("[PlanPersistenceService] Loading plan context: planId={}, fromStep={}", planId, fromStepIndex);

        ResearchPlan plan = loadPlan(planId);
        if (plan == null) {
            return "";
        }

        return serializer.buildExecutionContext(plan, fromStepIndex);
    }

    public String loadCurrentStepContext(String planId) {
        ResearchPlan plan = loadPlan(planId);
        if (plan == null) {
            return "";
        }
        return serializer.buildExecutionContext(plan, plan.getCurrentStepIndex());
    }

    public void updatePlanProgress(String planId, int currentStepIndex, ResearchPlan.PlanStatus status) {
        log.info("[PlanPersistenceService] Updating plan progress: planId={}, step={}, status={}",
                planId, currentStepIndex, status);

        ResearchPlan plan = loadPlan(planId);
        if (plan == null) {
            log.warn("[PlanPersistenceService] Cannot update non-existent plan: {}", planId);
            return;
        }

        plan.setCurrentStepIndex(currentStepIndex);
        plan.setStatus(status);

        savePlan(plan, planId);
    }

    public void updateStepResult(String planId, int stepIndex, ResearchStep.StepStatus status, String result) {
        log.info("[PlanPersistenceService] Updating step result: planId={}, stepIndex={}, status={}",
                planId, stepIndex, status);

        ResearchPlan plan = loadPlan(planId);
        if (plan == null) {
            log.warn("[PlanPersistenceService] Cannot update step in non-existent plan: {}", planId);
            return;
        }

        if (plan.getSteps() != null && stepIndex >= 0 && stepIndex < plan.getSteps().size()) {
            ResearchStep step = plan.getSteps().get(stepIndex);
            switch (status) {
                case IN_PROGRESS -> step.markInProgress();
                case COMPLETED -> step.markCompleted(result);
                case FAILED -> step.markFailed(result);
                default -> {
                    step.setStatus(status);
                    step.setResult(result);
                }
            }
            savePlan(plan, planId);
        }
    }

    public List<String> listPlans() {
        List<String> planIds = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storagePath,
                PLAN_FILE_PREFIX + "*" + PLAN_FILE_SUFFIX)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                String planId = fileName.substring(PLAN_FILE_PREFIX.length(),
                        fileName.length() - PLAN_FILE_SUFFIX.length());
                planIds.add(planId);
            }
        } catch (IOException e) {
            log.error("[PlanPersistenceService] Failed to list plans: {}", e.getMessage(), e);
        }

        return planIds;
    }

    public boolean deletePlan(String planId) {
        log.info("[PlanPersistenceService] Deleting plan: planId={}", planId);

        try {
            Path filePath = resolvePlanFile(planId);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("[PlanPersistenceService] Plan deleted: {}", planId);
            } else {
                log.warn("[PlanPersistenceService] Plan not found for deletion: {}", planId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("[PlanPersistenceService] Failed to delete plan: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean planExists(String planId) {
        Path filePath = resolvePlanFile(planId);
        return Files.exists(filePath);
    }

    private Path resolvePlanFile(String planId) {
        return storagePath.resolve(PLAN_FILE_PREFIX + planId + PLAN_FILE_SUFFIX);
    }

    private String generatePlanId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void ensureStorageDirectory() {
        try {
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
                log.info("[PlanPersistenceService] Created storage directory: {}", storagePath);
            }
        } catch (IOException e) {
            log.error("[PlanPersistenceService] Failed to create storage directory: {}", e.getMessage(), e);
            throw new PlanPersistenceException("Failed to create storage directory: " + storagePath, e);
        }
    }

    public static class PlanPersistenceException extends RuntimeException {
        public PlanPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
