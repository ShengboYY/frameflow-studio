package com.frameflow.studio.domain.asset.adapter.port;

import java.util.Optional;
import com.frameflow.studio.domain.asset.model.valobj.PackagedImage;

public interface IDemoMediaPort {
    Optional<PackagedImage> findCover(String mediaKey);
    PackagedImage resultFixture();
}
