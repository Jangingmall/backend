package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.BlockTag;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentErrorMessage;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentEditHistoryRepository historyRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ContentResponse.Detail getContent(Long productId, Long requesterId) {
        verifyProductOwner(productId, requesterId);
        Content content = contentRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        return ContentResponse.Detail.from(content);
    }

    @Transactional
    public ContentResponse.Detail bulkUpdateBlocks(ContentCommand.BulkUpdate command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));

        List<ContentBlock> newBlocks = command.blocks().stream()
            .map(input -> ContentBlock.create(
                content,
                (short) input.order(),
                input.tag(),
                input.imageUrl(),
                input.videoUrl(),
                input.text()
            ))
            .toList();

        content.replaceBlocks(newBlocks);
        Content saved = contentRepository.save(content);

        historyRepository.save(ContentEditHistory.record(
            saved.getId(), saved.getVersion(), EditedByType.ARTISAN, command.requesterId()
        ));

        return ContentResponse.Detail.from(saved);
    }

    @Transactional
    public ContentResponse.BlockEdit updateBlock(ContentCommand.BlockUpdate command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));

        ContentBlock block = contentBlockRepository.findByContentIdAndDisplayOrder(
                content.getId(), (short) command.blockOrder()
            )
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.BLOCK_NOT_FOUND.message()));

        applyBlockUpdate(block, command);
        content.touchVersion();
        Content saved = contentRepository.save(content);

        historyRepository.save(ContentEditHistory.record(
            saved.getId(), saved.getVersion(), EditedByType.ARTISAN, command.requesterId()
        ));

        return new ContentResponse.BlockEdit(saved.getId(), saved.getVersion(), ContentResponse.BlockView.from(block));
    }

    @Transactional(readOnly = true)
    public List<ContentResponse.VersionHistory> getVersionHistory(Long productId, Long requesterId) {
        verifyProductOwner(productId, requesterId);
        Content content = contentRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));

        return historyRepository.findAllByContentIdOrderByVersionAsc(content.getId()).stream()
            .map(ContentResponse.VersionHistory::from)
            .toList();
    }

    @Transactional
    public Content materializeFromBlocks(Long productId, List<ContentCommand.BlockInput> blockInputs, Long memberId) {
        Content content = contentRepository.findByProductId(productId)
            .orElseGet(() -> {
                Content created = Content.create(productId);
                return contentRepository.save(created);
            });

        List<ContentBlock> blocks = blockInputs.stream()
            .map(input -> ContentBlock.create(
                content,
                (short) input.order(),
                input.tag(),
                input.imageUrl(),
                input.videoUrl(),
                input.text()
            ))
            .toList();

        content.replaceBlocks(blocks);
        Content saved = contentRepository.save(content);

        historyRepository.save(ContentEditHistory.record(
            saved.getId(), saved.getVersion(), EditedByType.AI, memberId
        ));

        return saved;
    }

    private void applyBlockUpdate(ContentBlock block, ContentCommand.BlockUpdate command) {
        BlockTag tag = command.tag() != null ? command.tag() : block.getTag();
        block.replaceWith(tag, command.text(), command.imageUrl(), null);
    }

    private void verifyProductOwner(Long productId, Long requesterId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
        product.verifyOwner(requesterId);
    }
}
