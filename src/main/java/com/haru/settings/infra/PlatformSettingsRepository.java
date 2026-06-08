package com.haru.settings.infra;

import com.haru.settings.domain.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {

    Optional<PlatformSettings> findFirstByIsActiveTrueOrderBySettingVersionDesc();
}
