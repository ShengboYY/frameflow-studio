package com.frameflow.studio.cases.asset.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.domain.asset.adapter.port.IDemoMediaPort;

@Service
public class MediaResourceCaseImpl implements IMediaResourceCase {
    private final IDemoMediaPort media;

    public MediaResourceCaseImpl(IDemoMediaPort media) {
        this.media = media;
    }

    @Override
    public Optional<MediaResource> findCover(String mediaKey) {
        return media.findCover(mediaKey).map(MediaResource::from);
    }

    @Override
    public MediaResource resultFixture() {
        return MediaResource.from(media.resultFixture());
    }
}
