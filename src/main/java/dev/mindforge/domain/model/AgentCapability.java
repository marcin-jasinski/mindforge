package dev.mindforge.domain.model;

/**
 * Self-describing metadata an agent advertises: its name, what it does, the model
 * tier it requires, and a coarse cost estimate the orchestrator can plan around.
 */
public record AgentCapability(
    String name,
    String description,
    ModelTier requiredModelTier,
    CostTier estimatedCostTier
) {}
