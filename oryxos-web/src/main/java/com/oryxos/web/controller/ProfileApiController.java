package com.oryxos.web.controller;

import com.oryxos.core.agent.ProfileRegistry;
import com.oryxos.core.model.Profile;
import com.oryxos.web.dto.ApiResponse;
import java.util.Collection;
import org.springframework.web.bind.annotation.*;

/** Profile API：列出所有 Profile。 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {

    private final ProfileRegistry profileRegistry;

    public ProfileApiController(ProfileRegistry profileRegistry) {
        this.profileRegistry = profileRegistry;
    }

    @GetMapping
    public ApiResponse<Collection<Profile>> listProfiles() {
        return ApiResponse.ok(profileRegistry.getAll());
    }
}
