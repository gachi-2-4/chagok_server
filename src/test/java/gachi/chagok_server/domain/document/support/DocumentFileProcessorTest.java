package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.domain.document.support.DocumentFileProcessor.Prepared;
import gachi.chagok_server.domain.document.support.DocumentFileProcessor.UploadedFile;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileProcessorTest {

    private final DocumentFileProcessor processor = new DocumentFileProcessor();

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] pdf(int pages, boolean encrypted) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IntStream.range(0, pages).forEach(i -> document.addPage(new PDPage(PDRectangle.A4)));
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("사진은 긴 변을 2,000px로 줄이고 순서대로 PDF 한 개로 합친다")
    void preparesImages() throws IOException {
        // given
        List<UploadedFile> files = List.of(
                new UploadedFile("a.jpg", image(3000, 1000, "jpg")),
                new UploadedFile("b.png", image(800, 600, "png")));

        // when
        Prepared prepared = processor.prepare(files);

        // then
        assertThat(prepared.pages()).hasSize(2);
        assertThat(prepared.pages().get(0).width()).isEqualTo(2000);
        assertThat(prepared.pages().get(0).height()).isEqualTo(667);
        assertThat(prepared.pages().get(1).width()).isEqualTo(800);
        assertThat(prepared.originalFileName()).isNull();
        assertThat(prepared.thumbnail()).isNotEmpty();
        try (PDDocument merged = Loader.loadPDF(prepared.pdf())) {
            assertThat(merged.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("PDF는 페이지별 이미지로 바꾸고 원본 PDF와 파일 이름을 그대로 둔다")
    void preparesPdf() throws IOException {
        // given
        byte[] bytes = pdf(3, false);

        // when
        Prepared prepared = processor.prepare(List.of(new UploadedFile("계약서.pdf", bytes)));

        // then
        assertThat(prepared.pages()).hasSize(3);
        assertThat(prepared.pdf()).isEqualTo(bytes);
        assertThat(prepared.originalFileName()).isEqualTo("계약서.pdf");
        // A4를 150DPI로 그린 크기 (반올림 차이로 1px 오차가 날 수 있다)
        assertThat(prepared.pages().get(0).width()).isBetween(1239, 1241);
        assertThat(prepared.pages().get(0).height()).isBetween(1753, 1755);
    }

    @Test
    @DisplayName("암호가 걸린 PDF는 ENCRYPTED_PDF")
    void encryptedPdf() throws IOException {
        // given
        byte[] bytes = pdf(1, true);

        // when & then
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.pdf", bytes))), ErrorCode.ENCRYPTED_PDF);
    }

    @Test
    @DisplayName("PDF는 20페이지까지만 올릴 수 있다")
    void tooManyPages() throws IOException {
        // given
        byte[] bytes = pdf(21, false);

        // when & then
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.pdf", bytes))), ErrorCode.TOO_MANY_PAGES);
    }

    @Test
    @DisplayName("열 수 없는 파일은 CORRUPTED_FILE")
    void corruptedFile() {
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.pdf", new byte[]{1, 2, 3}))), ErrorCode.CORRUPTED_FILE);
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.jpg", new byte[]{1, 2, 3}))), ErrorCode.CORRUPTED_FILE);
    }

    @Test
    @DisplayName("사진과 PDF를 섞거나 PDF를 여러 개 올릴 수 없다")
    void invalidCombination() throws IOException {
        // given
        byte[] pdf = pdf(1, false);
        byte[] jpg = image(10, 10, "jpg");

        // when & then
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.pdf", pdf), new UploadedFile("b.jpg", jpg))),
                ErrorCode.INVALID_FILE_COMBINATION);
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.pdf", pdf), new UploadedFile("b.pdf", pdf))),
                ErrorCode.INVALID_FILE_COMBINATION);
    }

    @Test
    @DisplayName("지원하지 않는 형식은 UNSUPPORTED_FILE_TYPE")
    void unsupportedType() {
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.gif", new byte[]{1}))), ErrorCode.UNSUPPORTED_FILE_TYPE);
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.heic", new byte[]{1}))), ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("파일이 20MB를 넘으면 FILE_TOO_LARGE")
    void tooLarge() {
        // given
        byte[] big = new byte[(int) DocumentFileProcessor.MAX_FILE_BYTES + 1];

        // when & then
        assertError(() -> processor.prepare(List.of(new UploadedFile("a.jpg", big))), ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("사진은 10장까지만 올릴 수 있다")
    void tooManyImages() throws IOException {
        // given
        byte[] jpg = image(10, 10, "jpg");
        List<UploadedFile> files = IntStream.range(0, 11).mapToObj(i -> new UploadedFile(i + ".jpg", jpg)).toList();

        // when & then
        assertError(() -> processor.prepare(files), ErrorCode.INVALID_INPUT);
    }
}
