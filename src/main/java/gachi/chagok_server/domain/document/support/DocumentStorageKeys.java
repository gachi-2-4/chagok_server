package gachi.chagok_server.domain.document.support;

/**
 * 서류 파일의 저장소 키. 한 서류의 파일은 모두 같은 접두사 아래에 둬서 한 번에 지울 수 있다.
 */
public final class DocumentStorageKeys {

    private DocumentStorageKeys() {
    }

    /** 한 사용자의 모든 서류 파일 (회원 탈퇴 때 남은 파일까지 정리) */
    public static String userPrefix(Long userId) {
        return "documents/" + userId + "/";
    }

    public static String prefix(Long userId, Long documentId) {
        return "documents/" + userId + "/" + documentId + "/";
    }

    public static String pdf(Long userId, Long documentId) {
        return prefix(userId, documentId) + "original.pdf";
    }

    public static String thumbnail(Long userId, Long documentId) {
        return prefix(userId, documentId) + "thumbnail.jpg";
    }

    public static String page(Long userId, Long documentId, int pageNo) {
        return prefix(userId, documentId) + "pages/" + pageNo + ".jpg";
    }
}
