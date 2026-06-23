package dev.mindforge.domain.model;

/**
 * A single pipeline processing step. Implementations are stateless and registered
 * by name; the orchestrator discovers them through the registry rather than calling
 * them directly, keeping the pipeline open for extension (new agents) and closed
 * for modification.
 */
public interface Agent {

    String name();

    AgentCapability capability();

    AgentResult execute(AgentContext context);
}
