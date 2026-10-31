package com.frameflow.studio.cases.asset;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.frameflow.studio.cases.asset.impl.*;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.template.impl.TemplateCatalogCaseImpl;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;
import com.frameflow.studio.infrastructure.adapter.port.asset.LocalAssetStoragePort;
import com.frameflow.studio.infrastructure.adapter.port.asset.PackagedDemoMediaPort;
import com.frameflow.studio.infrastructure.adapter.repository.template.TemplateCatalogRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssetCasesTest {
    @TempDir Path root;
    final IUploadedAssetRepository repository = mock(IUploadedAssetRepository.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final TransactionStatus status = mock(TransactionStatus.class);
    final UUID owner = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-31T00:00:00.123456789Z");
    IAssetStoragePort storage;
    UploadAssetCaseImpl upload;

    @BeforeEach
    void prepare() {
        storage = new LocalAssetStoragePort(root);
        when(transactions.getTransaction(any())).thenReturn(status);
        upload = uploader(storage);
    }

    private UploadAssetCaseImpl uploader(IAssetStoragePort target) {
        return new UploadAssetCaseImpl(new TemplateCatalogCaseImpl(new TemplateCatalogRepository(new JsonMapper(),
                new org.springframework.core.io.ClassPathResource("templates/catalog.json"),
                java.util.Set.of("portrait-cover", "postcard-cover"))),
                target, repository, Clock.fixed(now, ZoneOffset.UTC), transactions);
    }

    @Test
    void finalizationResolvesOnlyRegisteredFixtureAndNeverArbitraryReferences() {
        var media = new MediaResourceCaseImpl(new PackagedDemoMediaPort(), repository, storage);
        assertThat(media.prepareResult("demo:mock-result").bytes()).isNotEmpty();
        for (String reference : List.of("demo:portrait-cover", "../mock-result.jpg", "file:/tmp/result.jpg")) {
            assertThatThrownBy(() -> media.prepareResult(reference)).isInstanceOf(AssetOperationException.class)
                    .hasMessage("UNAVAILABLE");
        }
        verifyNoInteractions(repository, transactions);
    }

    @Test
    void fileIsPublishedBeforeShortTransactionAndResponseWaitsForCommit() throws Exception {
        byte[] bytes = fixture();
        doAnswer(call -> {
            var asset = call.getArgument(0, UploadedAsset.class);
            assertThat(storage.read(asset.contentKey())).isEqualTo(bytes);
            assertThat(asset.createdAt()).isEqualTo(Instant.parse("2026-10-31T00:00:00.123456Z"));
            return null;
        }).when(repository).insert(any());
        var result = upload.upload(owner, "travel-postcard", new ByteArrayInputStream(bytes));
        assertThat(result.ownerId()).isEqualTo(owner);
        assertThat(result.mediaType()).isEqualTo("image/jpeg");
        var order = inOrder(repository, transactions);
        order.verify(transactions).getTransaction(argThat(def -> def.getPropagationBehavior() == 3 && def.getIsolationLevel() == 2));
        order.verify(repository).insert(any());
        order.verify(transactions).commit(status);
    }

    @Test
    void insertOrCommitFailureDoesNotReturnAssetAndCompensatesStableFile() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated")).when(repository).insert(any());
        assertThatThrownBy(() -> upload.upload(owner, "portrait-card", new ByteArrayInputStream(fixture())))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(transactions).rollback(status);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
        reset(repository);
        doThrow(new DataAccessResourceFailureException("commit failed")).when(transactions).commit(status);
        assertThatThrownBy(() -> upload.upload(owner, "portrait-card", new ByteArrayInputStream(fixture())))
                .isInstanceOf(DataAccessResourceFailureException.class);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void failedCleanupPreservesOriginalFailureAndUnknownTemplateDoesNoIo() throws Exception {
        var broken = mock(IAssetStoragePort.class);
        assertThatThrownBy(() -> uploader(broken).upload(owner, "unknown", new ByteArrayInputStream(fixture())))
                .isInstanceOf(AssetOperationException.class).hasMessage("NOT_FOUND");
        verifyNoInteractions(broken, transactions, repository);
        var original = new DataAccessResourceFailureException("original");
        doThrow(original).when(repository).insert(any());
        doThrow(new IllegalStateException("cleanup")).when(broken).discard(any());
        // Use the key associated with the generated id to preserve the domain invariant.
        when(broken.prepare(any(), any())).thenAnswer(call -> new com.frameflow.studio.domain.asset.model.valobj.StoredImage(
                "upload:" + call.getArgument(0), "image/jpeg", 12));
        assertThatThrownBy(() -> uploader(broken).upload(owner, "portrait-card", new ByteArrayInputStream(fixture())))
                .isSameAs(original);
        verify(broken).discard(any());
    }

    @Test
    void templateLimitRejectsBeforeMetadataAndRemovesTemporaryFile() throws Exception {
        byte[] bytes = java.util.Arrays.copyOf(fixture(), 5242881);
        assertThatThrownBy(() -> upload.upload(owner, "portrait-card", new ByteArrayInputStream(bytes)))
                .isInstanceOf(AssetOperationException.class).hasMessage("TOO_LARGE");
        verifyNoInteractions(repository, transactions);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void resolutionKeepsOrderDuplicatesAndRechecksDiskForWorkerAndPreview() throws Exception {
        var first = upload.upload(owner, "portrait-card", new ByteArrayInputStream(fixture()));
        var second = upload.upload(owner, "portrait-card", new ByteArrayInputStream(fixture()));
        for (var asset : List.of(first, second)) {
            when(repository.findOwned(asset.assetId(), owner)).thenReturn(Optional.of(new UploadedAsset(asset.assetId(),
                    owner, asset.contentReference(), asset.mediaType(), asset.sizeBytes(), asset.createdAt())));
        }
        var resolver = new ResolveTaskAssetsCaseImpl(repository, storage);
        var ids = List.of(second.assetId(), first.assetId(), second.assetId());
        assertThat(resolver.resolve(owner, ids)).extracting(value -> value.assetId()).containsExactlyElementsOf(ids);
        resolver.check(owner, ids);
        var media = new MediaResourceCaseImpl(new PackagedDemoMediaPort(), repository, storage);
        assertThat(media.input(owner, first.assetId()).bytes()).isEqualTo(fixture());
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> resolver.resolve(other, ids)).hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> media.input(other, first.assetId())).hasMessage("NOT_FOUND");
        storage.discard(first.contentReference());
        assertThatThrownBy(() -> resolver.check(owner, ids)).hasMessage("UNAVAILABLE");
        assertThatThrownBy(() -> media.input(owner, first.assetId())).hasMessage("UNAVAILABLE");
    }

    private byte[] fixture() throws Exception {
        return new org.springframework.core.io.ClassPathResource("demo-media/mock-result.jpg").getContentAsByteArray();
    }
}
