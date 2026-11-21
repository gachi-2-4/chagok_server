package gachi.chagok_server.domain.document.controller;

import gachi.chagok_server.domain.document.dto.DocumentDtos.*;
import gachi.chagok_server.domain.document.entity.DocCategory;
import gachi.chagok_server.domain.document.entity.DocType;
import gachi.chagok_server.domain.document.entity.PriorityLevel;
import gachi.chagok_server.domain.document.entity.UploadSource;
import gachi.chagok_server.domain.document.service.DocumentCommandService;
import gachi.chagok_server.domain.document.service.DocumentFieldService;
import gachi.chagok_server.domain.document.service.DocumentQueryService;
import gachi.chagok_server.domain.document.service.DocumentQueryService.Filter;
import gachi.chagok_server.domain.document.service.DocumentUploadService;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor.UploadedFile;
import gachi.chagok_server.global.common.ApiResponse;
import gachi.chagok_server.global.common.PageResponse;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import gachi.chagok_server.global.security.InternalKeyVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentUploadService uploadService;
    private final DocumentQueryService queryService;
    private final DocumentCommandService commandService;
    private final DocumentFieldService fieldService;
    private final InternalKeyVerifier internalKey;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(@AuthenticationPrincipal Long userId,
                                                              @RequestParam("files") List<MultipartFile> files,
                                                              @RequestParam Long boxId,
                                                              @RequestParam(required = false) String title,
                                                              @RequestParam(required = false) UploadSource source,
                                                              @RequestParam(required = false) String ocrResult) {
        List<UploadedFile> uploaded = files.stream().map(DocumentController::read).toList();
        UploadResponse response = uploadService.upload(userId, uploaded, boxId, title, source, ocrResult);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ConfirmResponse> confirm(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.ok(commandService.confirm(userId, id));
    }

    @GetMapping
    public ApiResponse<PageResponse<ListItem>> list(@AuthenticationPrincipal Long userId,
                                                    @RequestParam(required = false) Long boxId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) PriorityLevel priority,
                                                    @RequestParam(required = false) Integer dueWithinDays,
                                                    @RequestParam(required = false) Boolean hasCost,
                                                    @RequestParam(required = false) List<DocCategory> docCategory,
                                                    @RequestParam(required = false) DocType docType,
                                                    @RequestParam(defaultValue = "RECENT") Sort sort,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Filter filter = new Filter(boxId, keyword, priority, dueWithinDays, hasCost, docCategory, docType);
        return ApiResponse.ok(queryService.list(userId, filter, sort, page, size));
    }

    /**
     * 서류 상세. 앱은 Bearer 토큰으로, AI 서버는 X-Internal-Key + userId 쿼리로 부른다.
     * AI 서버 호출에만 OCR 원문(fullText)을 담는다.
     */
    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                      @RequestHeader(value = InternalKeyVerifier.HEADER, required = false) String key,
                                      @RequestParam(name = "userId", required = false) Long ownerId) {
        if (key != null) {
            internalKey.verify(key);
            if (ownerId == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return ApiResponse.ok(queryService.internalDetail(ownerId, id));
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(queryService.detail(userId, id));
    }

    @GetMapping("/{id}/original")
    public ApiResponse<OriginalResponse> original(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                  @RequestParam(required = false) Integer page) {
        return ApiResponse.ok(queryService.original(userId, id, page));
    }

    @GetMapping("/{id}/download")
    public ApiResponse<DownloadResponse> download(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                  @RequestParam(required = false) String type) {
        return ApiResponse.ok(queryService.download(userId, id, type));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RenameResponse> rename(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                              @RequestBody RenameRequest request) {
        return ApiResponse.ok(commandService.rename(userId, id, request.title(), request.boxId()));
    }

    @PatchMapping("/{id}/fields")
    public ApiResponse<Detail> editFields(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                          @Valid @RequestBody FieldsRequest request) {
        return ApiResponse.ok(fieldService.edit(userId, id, request));
    }

    @PatchMapping("/{id}/checklist/{checkKey}")
    public ApiResponse<CheckResponse> changeCheck(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                  @PathVariable String checkKey, @Valid @RequestBody CheckRequest request) {
        return ApiResponse.ok(commandService.changeCheck(userId, id, checkKey, request.done()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        commandService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static UploadedFile read(MultipartFile file) {
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
        }
    }
}
