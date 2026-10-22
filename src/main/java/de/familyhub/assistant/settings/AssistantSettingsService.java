package de.familyhub.assistant.settings;

import org.springframework.stereotype.Service;

@Service
public class AssistantSettingsService {

    private final AssistantSettingsRepository repository;

    public AssistantSettingsService(AssistantSettingsRepository repository) {
        this.repository = repository;
    }

    public AssistantSettings current() {
        return repository.findById(AssistantSettings.ID).orElseGet(AssistantSettings::defaults);
    }

    public AssistantSettings update(AssistantSettings settings) {
        return repository.save(settings.withId());
    }
}
