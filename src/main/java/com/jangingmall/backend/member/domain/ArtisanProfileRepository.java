package com.jangingmall.backend.member.domain;

import java.util.Optional;

public interface ArtisanProfileRepository {
    ArtisanProfile save(ArtisanProfile profile);
    Optional<ArtisanProfile> findById(Long id);
    void flush();
}
