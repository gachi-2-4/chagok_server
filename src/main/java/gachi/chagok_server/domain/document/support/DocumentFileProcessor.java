package gachi.chagok_server.domain.document.support;

import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 올린 파일을 검사하고, 페이지 이미지·썸네일·PDF로 가공한다.
 * 사진은 긴 변 2,000px로 줄여 PDF 한 개로 합친다 (AI 문서 분석은 요청당 파일 1개, 20MB까지만 받음).
 * PDF는 페이지별 이미지로 바꿔 둔다 (앱은 이미지 뷰어 하나로 처리).
 */
@Component
public class DocumentFileProcessor {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final int MAX_PDF_PAGES = 20;
    static final int MAX_IMAGES = 10;
    static final int MAX_LONG_SIDE = 2000;
    static final int THUMBNAIL_LONG_SIDE = 400;
    /** 렌더링 해상도. A4가 1240x1754px이 된다. */
    static final float PDF_RENDER_DPI = 150f;

    /** 올라온 파일 한 개 */
    public record UploadedFile(String fileName, byte[] bytes) {
    }

    public record PageImage(byte[] jpeg, int width, int height) {
    }

    /**
     * 가공 결과.
     *
     * @param pdf              원본 PDF, 또는 사진을 페이지 순서대로 합친 PDF
     * @param originalFileName 올린 PDF 파일 이름 (사진이면 null)
     */
    public record Prepared(List<PageImage> pages, byte[] pdf, String originalFileName, byte[] thumbnail) {
    }

    private enum Kind { PDF, IMAGE }

    public Prepared prepare(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<Kind> kinds = files.stream().map(f -> kindOf(f.fileName())).toList();
        for (UploadedFile file : files) {
            if (file.bytes().length > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
        }
        boolean hasPdf = kinds.contains(Kind.PDF);
        boolean hasImage = kinds.contains(Kind.IMAGE);
        if (hasPdf && hasImage) {
            throw new BusinessException(ErrorCode.INVALID_FILE_COMBINATION);
        }

        Prepared prepared = hasPdf ? preparePdf(files) : prepareImages(files);
        return prepared;
    }

    private Prepared preparePdf(List<UploadedFile> files) {
        if (files.size() != 1) {
            // PDF는 1개만 올릴 수 있다.
            throw new BusinessException(ErrorCode.INVALID_FILE_COMBINATION);
        }
        UploadedFile file = files.get(0);
        List<PageImage> pages = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(file.bytes())) {
            if (pdf.isEncrypted()) {
                throw new BusinessException(ErrorCode.ENCRYPTED_PDF);
            }
            if (pdf.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new BusinessException(ErrorCode.TOO_MANY_PAGES);
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                pages.add(toPageImage(renderer.renderImageWithDPI(i, PDF_RENDER_DPI)));
            }
        } catch (InvalidPasswordException e) {
            throw new BusinessException(ErrorCode.ENCRYPTED_PDF, e);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
        }
        if (pages.isEmpty()) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE);
        }
        return new Prepared(pages, file.bytes(), file.fileName(), thumbnail(pages.get(0)));
    }

    private Prepared prepareImages(List<UploadedFile> files) {
        if (files.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<PageImage> pages = new ArrayList<>();
        for (UploadedFile file : files) {
            BufferedImage image;
            try {
                image = ImageIO.read(new ByteArrayInputStream(file.bytes()));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
            }
            if (image == null) {
                throw new BusinessException(ErrorCode.CORRUPTED_FILE);
            }
            pages.add(toPageImage(image));
        }
        return new Prepared(pages, mergeToPdf(pages), null, thumbnail(pages.get(0)));
    }

    private static Kind kindOf(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return Kind.PDF;
        }
        // HEIC는 Java 기본 이미지 라이브러리가 읽지 못해 아직 지원하지 않는다.
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            return Kind.IMAGE;
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    /** 긴 변을 2,000px 이하로 줄이고 JPEG로 인코딩한다. */
    private PageImage toPageImage(BufferedImage source) {
        BufferedImage scaled = scaleDown(source, MAX_LONG_SIDE);
        return new PageImage(encodeJpeg(scaled), scaled.getWidth(), scaled.getHeight());
    }

    private byte[] thumbnail(PageImage firstPage) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(firstPage.jpeg()));
            return encodeJpeg(scaleDown(image, THUMBNAIL_LONG_SIDE));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
        }
    }

    /** 사진들을 페이지 순서대로 합친 PDF를 만든다. */
    byte[] mergeToPdf(List<PageImage> pages) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PageImage page : pages) {
                PDPage pdfPage = new PDPage(new PDRectangle(page.width(), page.height()));
                pdf.addPage(pdfPage);
                PDImageXObject image = PDImageXObject.createFromByteArray(pdf, page.jpeg(), "page");
                try (PDPageContentStream content = new PDPageContentStream(pdf, pdfPage)) {
                    content.drawImage(image, 0, 0, page.width(), page.height());
                }
            }
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
        }
    }

    static BufferedImage scaleDown(BufferedImage source, int maxLongSide) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        double ratio = longSide > maxLongSide ? (double) maxLongSide / longSide : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        // JPEG는 투명도를 못 담으므로 흰 배경의 RGB로 그린다.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CORRUPTED_FILE, e);
        }
    }
}
