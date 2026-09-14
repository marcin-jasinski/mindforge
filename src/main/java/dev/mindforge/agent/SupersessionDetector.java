package dev.mindforge.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mindforge.domain.model.CandidateSection;
import dev.mindforge.domain.model.Claim;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.SupersessionProposal;
import dev.mindforge.domain.port.AIGateway;
import dev.mindforge.infrastructure.ai.ModelJson;
import dev.mindforge.infrastructure.ai.PromptLoader;

/** Proposes which shown sections a run's claims correct; code keeps only proposals it can verify. */
public class SupersessionDetector {

    public static final String VERSION = "1";
    public static final ModelTier TIER = ModelTier.LARGE;

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public SupersessionDetector(AIGateway gateway, PromptLoader prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /** @param claims claims whose {@code targetPath} is the page they were written into */
    public List<SupersessionProposal> detect(List<Claim> claims, List<CandidateSection> candidates) {
        String claimList = claims.stream()
            .map(claim -> "* [" + claim.targetPath() + "] " + claim.text())
            .collect(Collectors.joining("\n"));
        String sections = candidates.stream()
            .map(section -> "=== " + section.path() + "#" + section.anchor() + " — " + section.heading() + "\n"
                + section.text())
            .collect(Collectors.joining("\n"));
        String prompt = prompts.render("supersession_detector", Map.of("claims", claimList, "sections", sections));
        Output output = ModelJson.read(getClass().getSimpleName(),
            gateway.complete(TIER, prompt, DeadlineProfile.BACKGROUND).content(), Output.class);
        return output.proposals() == null ? List.of() : output.proposals();
    }

    record Output(List<SupersessionProposal> proposals) {}
}
