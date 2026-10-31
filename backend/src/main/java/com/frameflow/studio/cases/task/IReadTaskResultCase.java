package com.frameflow.studio.cases.task;

import java.util.UUID;
import com.frameflow.studio.cases.asset.model.MediaResource;

public interface IReadTaskResultCase {
    MediaResource read(UUID ownerId, UUID taskId);
}
