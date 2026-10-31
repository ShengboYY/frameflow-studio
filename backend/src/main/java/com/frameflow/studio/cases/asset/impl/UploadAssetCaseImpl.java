package com.frameflow.studio.cases.asset.impl;

import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.IUploadAssetCase;
import com.frameflow.studio.cases.asset.model.AssetDescriptor;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.template.ITemplateCatalogCase;
import com.frameflow.studio.cases.template.model.TemplateValidation.ImageInput;
import com.frameflow.studio.cases.template.model.TemplateValidation.Violation;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;
import com.frameflow.studio.domain.asset.model.valobj.AssetException;
import static com.frameflow.studio.domain.asset.model.valobj.AssetException.Reason.*;

@Service
public class UploadAssetCaseImpl implements IUploadAssetCase {
    private static final Logger log = LoggerFactory.getLogger(UploadAssetCaseImpl.class);
    private final ITemplateCatalogCase templates;
    private final IAssetStoragePort storage;
    private final IUploadedAssetRepository repository;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public UploadAssetCaseImpl(ITemplateCatalogCase templates, IAssetStoragePort storage,
                               IUploadedAssetRepository repository, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.templates = templates;
        this.storage = storage;
        this.repository = repository;
        this.clock = clock;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public AssetDescriptor upload(UUID ownerId, String templateId, InputStream input) {
        try {
            return performUpload(ownerId, templateId, input);
        } catch (AssetException failure) {
            throw AssetOperationException.from(failure);
        }
    }

    private AssetDescriptor performUpload(UUID ownerId, String templateId, InputStream input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Upload must start outside a transaction");
        }
        templates.findById(templateId).orElseThrow(() -> new AssetException(NOT_FOUND));
        UUID assetId = UUID.randomUUID();
        var image = storage.prepare(assetId, input);
        try {
            var validation = templates.validateImage(templateId, new ImageInput(image.mediaType(), image.sizeBytes()))
                    .orElseThrow(() -> new AssetException(NOT_FOUND));
            if (validation.violations().contains(Violation.IMAGE_SIZE)) throw new AssetException(TOO_LARGE);
            if (validation.violations().contains(Violation.IMAGE_TYPE)) throw new AssetException(UNSUPPORTED_TYPE);
            storage.publish(image.contentKey());
            var asset = new UploadedAsset(assetId, ownerId, image.contentKey(), image.mediaType(), image.sizeBytes(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
            transaction.executeWithoutResult(status -> repository.insert(asset));
            return AssetDescriptor.from(asset);
        } catch (RuntimeException exception) {
            try {
                storage.discard(image.contentKey());
            } catch (RuntimeException cleanupFailure) {
                // Do not log the filesystem location, uploaded content or SQL exception details.
                log.warn("Upload compensation failed for asset {}", assetId);
            }
            throw exception;
        }
    }
}
