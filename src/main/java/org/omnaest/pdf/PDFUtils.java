/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
/*

	Copyright 2017 Danny Kunz

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.


*/
package org.omnaest.pdf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.omnaest.utils.MapperUtils;
import org.omnaest.utils.SimpleExceptionHandler;
import org.omnaest.utils.StringUtils;
import org.omnaest.utils.exception.handler.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PDFUtils
{
    private static Logger LOG = LoggerFactory.getLogger(PDFUtils.class);

    public static interface PDFLoader
    {

        PDFBuilder createEmptyPDF();

        PDFBuilder loadPDF(byte[] data) throws InvalidPasswordException, IOException;

        PDFBuilder loadPDF(byte[] data, Function<Exception, PDFBuilder> exceptionHandler);

        /**
         * Tries to load the given pdf data, but creates a blank pdf if that fails for any reason.
         * 
         * @param data
         * @return
         */
        PDFBuilder loadPDFOrCreateEmpty(byte[] data);

        PDFBuilder loadPDF(File file) throws InvalidPasswordException, IOException;

        PDFLoader useExceptionHandler(ExceptionHandler exceptionHandler);

    }

    public static interface TextSizeProvider
    {
        public int getSize();
    }

    public enum TextSize implements TextSizeProvider
    {
        LARGE(16), NORMAL(12), SMALL(8), VERY_SMALL(6), ULTRA_SMALL(4);

        private int size;

        private TextSize(int size)
        {
            this.size = size;
        }

        @Override
        public int getSize()
        {
            return this.size;
        }

    }

    /**
     * @see DisplayResolution
     * @author omnaest
     */
    public static interface ResolutionProvider
    {
        public int getWidth();

        public int getHeight();
    }

    /**
     * Provider of typical screen dimensions
     * 
     * @author omnaest
     */
    public enum DisplayResolution implements ResolutionProvider
    {
        _800x600(800, 600),
        _800x300(800, 300),
        _1440x900(1440, 900),
        _1280x800(1280, 800),
        _1280x1024(1280, 1024),
        _800x1280(800, 1200),
        _640x480(640, 480),
        _320x240(320, 240);

        private int width;
        private int height;

        private DisplayResolution(int width, int height)
        {
            this.width = width;
            this.height = height;
        }

        @Override
        public int getWidth()
        {
            return this.width;
        }

        @Override
        public int getHeight()
        {
            return this.height;
        }

        public static ResolutionProvider of(int width, int height)
        {
            return new ResolutionProvider()
            {
                @Override
                public int getWidth()
                {
                    return width;
                }

                @Override
                public int getHeight()
                {
                    return height;
                }
            };
        }
    }

    public static interface ElementProcessor<E>
    {
        public void handle(PDFBuilderWithPage page, E element);
    }

    public static interface RawElementProcessor<E>
    {
        public void handle(E element);
    }

    public static interface PDFBuilderWithPage extends PDFBuilder
    {
        /**
         * Sets the {@link PdfFont}
         * 
         * @param pdfFont
         * @return
         */
        PDFBuilderWithPage withFont(PdfFont pdfFont);

        /**
         * Adds a text to the page
         * 
         * @param text
         * @return
         */
        PDFBuilderWithPage addText(String text);

        /**
         * Adds a text with the given {@link TextSize} to the page
         * 
         * @param textSize
         * @param text
         * @return
         */
        PDFBuilderWithPage addText(TextSizeProvider textSize, String text);

        /**
         * Adds all {@link String}s from the given {@link Iterable} to the page as texts, each on a new line
         * 
         * @param texts
         * @return
         */
        PDFBuilderWithPage addText(Iterable<String> texts);

        /**
         * Similar to {@link #addText(Iterable)}
         * 
         * @param texts
         * @return
         */
        PDFBuilderWithPage addText(String... texts);

        /**
         * Similar to {@link #addText(Iterable)} but allows to specify the {@link TextSize}
         * 
         * @param textSize
         * @param texts
         * @return
         */
        PDFBuilderWithPage addText(TextSizeProvider textSize, Iterable<String> texts);

        /**
         * Similar to {@link #addText(TextSizeProvider, Iterable)}
         * 
         * @param textSize
         * @param texts
         * @return
         */
        PDFBuilderWithPage addText(TextSizeProvider textSize, String... texts);

        PDFBuilderWithPage addText(Consumer<TextOptions> textOptionsConsumer, String... texts);

        /**
         * Similar to {@link #addBlankTextLine()} with {@link TextSize#NORMAL}
         * 
         * @return
         */
        PDFBuilderWithPage addBlankTextLine();

        /**
         * Adds a blank text line with the given {@link TextSize}
         * 
         * @param textSize
         * @return
         */
        PDFBuilderWithPage addBlankTextLine(TextSizeProvider textSize);

        PDFBuilderWithPage addBlankTextLines(TextSizeProvider textSize, int numberOfLines);

        /**
         * Jumps to the given line number assuming the given {@link TextSize}. This can also jump back and cause overlapping texts.
         * 
         * @param textSize
         * @param lineNumber
         * @return
         */
        PDFBuilderWithPage gotoLine(TextSizeProvider textSize, int lineNumber);

        PDFBuilderWithPage addTitle(String title);

        PDFBuilderWithPage addSubTitle(String subTitle);

        @Override
        PDFBuilderWithPage getPage(int pageIndex);

        /**
         * Returns the last page
         * 
         * @return
         */
        PDFBuilderWithPage getLastPage();

        /**
         * Adds a footer to the page
         * 
         * @param footer
         * @return
         */
        PDFBuilderWithPage addFooter(String footer);

        /**
         * Adds one or more footers to the page
         * 
         * @param footers
         * @return
         */
        PDFBuilderWithPage addFooter(String... footers);

        <E> PDFBuilderWithPage withElements(Stream<E> elements, ElementProcessor<E> processor);

        <E> PDFBuilderWithPage withElements(Collection<E> elements, ElementProcessor<E> processor);

        <E> PDFBuilderWithPage withRawElements(Collection<E> elements, RawElementProcessor<E> processor);

        PDFBuilderWithPage addPageBreakListener(Consumer<PDFBuilderWithPage> listener);

        /**
         * Defines multiple columns during the lifetime of the given consumer. Each {@link #addText(String)} call will then iterate through each column before
         * changing the row.
         * 
         * @param numberOfColumns
         * @param columnBuilderConsumer
         * @return
         */
        PDFBuilderWithPage withColumns(int numberOfColumns, Consumer<PDFBuilderWithPage> columnBuilderConsumer);

        /**
         * Similar to {@link #withColumns(int, Consumer)} but allows to specify a column weight for its width
         * 
         * @param numberOfColumns
         * @param columnWeightFunction
         * @param columnBuilderConsumer
         * @return
         */
        PDFBuilderWithPage withColumns(int numberOfColumns, IntToDoubleFunction columnWeightFunction, Consumer<PDFBuilderWithPage> columnBuilderConsumer);

        /**
         * Similar to {@link #withColumns(int, IntToDoubleFunction, Consumer)} but provides a {@link List} of column weights for each column one.
         * 
         * @param columnWeights
         * @param columnBuilderConsumer
         * @return
         */
        PDFBuilderWithPage withColumns(List<Double> columnWeights, Consumer<PDFBuilderWithPage> columnBuilderConsumer);

        /**
         * Adds a given PNG image at the current offset and moves the offset pointer
         * 
         * @param data
         * @param imageName
         * @param width
         * @param height
         * @return
         */
        PDFBuilderWithPage addPNG(byte[] data, String imageName, int width, int height);

        /**
         * Similar to {@link #addPNG(byte[], String, int, int)} with the default image name 'Image1','Image2', ...
         * 
         * @param data
         * @param width
         * @param height
         * @return
         */
        PDFBuilderWithPage addPNG(byte[] data, int width, int height);

        /**
         * Similar to {@link #addPNG(byte[], int, int)} with a given {@link DisplayResolution}
         * 
         * @see ResolutionProvider
         * @see DisplayResolution
         * @param data
         * @param displayResolution
         * @return
         */
        PDFBuilderWithPage addPNG(byte[] data, ResolutionProvider displayResolution);

        /**
         * Adds a given PNG image as background without changing the current offset.
         * 
         * @param data
         * @param imageName
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName);

        /**
         * Similar to {@link #addPNGAsBackground(byte[], String)} with a default image name like 'Image1', 'Image2', ...
         * 
         * @param data
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data);

        /**
         * Similar to {@link #addPNGAsBackground(byte[], String)} with the possibility to specify a width and height
         * 
         * @param data
         * @param imageName
         * @param width
         * @param height
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, int width, int height);

        /**
         * Similar to {@link #addPNGAsBackground(byte[], String)} with the possibility to specify a {@link DisplayResolution}
         * 
         * @see ResolutionProvider
         * @see DisplayResolution
         * @param data
         * @param imageName
         * @param displayResolution
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, ResolutionProvider displayResolution);

        /**
         * Similar to {@link #addPNGAsBackground(byte[])} with the possibility to specify a {@link DisplayResolution}
         * 
         * @param data
         * @param displayResolution
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data, ResolutionProvider displayResolution);

        /**
         * Similar to {@link #addPNGAsBackground(byte[], String, int, int)} but allows to specify a left and top offset as ratio of the page
         * 
         * @param data
         * @param imageName
         * @param leftAsRatio
         * @param topAsRatio
         * @param width
         * @param height
         * @return
         */
        PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, double leftAsRatio, double topAsRatio, int width, int height);

        /**
         * Tests the left over page height compared to the full page height and if the given {@link Predicate} test returns true, the given {@link Consumer} is
         * run.
         * 
         * @param ratioFilter
         * @param pageConsumer
         * @return
         */
        PDFBuilderWithPage ifWithRatioOfPageLeft(DoublePredicate ratioFilter, Consumer<PDFBuilderWithPage> pageConsumer);

    }

    public static interface PagesProcessor
    {
        public void process(Stream<PDFBuilderWithPage> pages);
    }

    public static interface PageProcessor extends Consumer<PDFBuilderWithPage>
    {
    }

    public static interface PDFBuilder
    {

        PDFWriter build();

        /**
         * Adds a blank page to the pdf
         * 
         * @return
         */
        PDFBuilderWithPage addBlankPage();

        /**
         * Similar to {@link #addBlankPage()} but allows to specify the {@link DisplayResolution}
         * 
         * @param displayResolution
         * @return
         */
        PDFBuilderWithPage addBlankPage(ResolutionProvider displayResolution);

        PDFBuilderWithPage getPage(int pageIndex);

        PDFBuilderWithPage addPagesOfFurtherPDF(byte[] pdf) throws InvalidPasswordException, IOException;

        PDFBuilderWithPage addPagesOfFurtherPDF(byte[] pdf, SimpleExceptionHandler exceptionHandler);

        PDFBuilderWithPage addPagesOfFurtherPDFSilently(byte[] pdf);

        PDFBuilderWithPage addPagesOfFurtherPDFSilently(Collection<byte[]> pdfs);

        PDFBuilderWithPage addPageWithPNG(byte[] png, ResolutionProvider displayResolution);

        /**
         * Processes the given {@link Stream} of pages. <br>
         * <br>
         * Resets the current cursor to the last page.
         * 
         * @see #forEachPage(PageProcessor)
         * @param processor
         * @return
         */
        PDFBuilder processPages(PagesProcessor processor);

        /**
         * Processes all available pages.<br>
         * <br>
         * Resets the current cursor position
         * 
         * @see #processPages(PagesProcessor)
         * @param processor
         * @return
         */
        PDFBuilder forEachPage(PageProcessor processor);

    }

    public static interface PDFWriter
    {
        /**
         * Writing the pdf to {@link File}
         *
         * @param pdfFile
         * @throws IOException
         */
        void writeTo(File pdfFile) throws IOException;

        /**
         * Similar to {@link #writeTo(File)} but using the given {@link SimpleExceptionHandler} for handling {@link Exception}s
         *
         * @param pdfFile
         * @param handler
         */
        void writeTo(File pdfFile, SimpleExceptionHandler handler);

        /**
         * Similar to {@link #writeTo(File)} without throwing an {@link IOException}
         *
         * @param pdfFile
         */
        void writeSilentlyTo(File pdfFile);

        InputStream get();

        byte[] getAsByteArray();

        Stream<String> getAsTextLines();

        String getAsText();

    }

    private static class PDFLoaderImpl implements PDFLoader
    {
        private PDDocument       document         = null;
        private ExceptionHandler exceptionHandler = ExceptionHandler.rethrowingExceptionHandler();

        @Override
        public PDFBuilder loadPDF(byte[] data) throws InvalidPasswordException, IOException
        {
            this.document = PDDocument.load(data);
            this.document.setAllSecurityToBeRemoved(true);
            return this.newPDFBuilderWithPage();
        }

        @Override
        public PDFBuilder loadPDF(byte[] data, Function<Exception, PDFBuilder> exceptionHandler)
        {
            PDFBuilder result = null;
            try
            {
                result = this.loadPDF(data);
            }
            catch (Exception e)
            {
                if (exceptionHandler != null)
                {
                    result = exceptionHandler.apply(e);
                }
            }
            return result;
        }

        @Override
        public PDFBuilder loadPDFOrCreateEmpty(byte[] data)
        {
            return this.loadPDF(data, e -> this.createEmptyPDF());
        }

        @Override
        public PDFBuilder loadPDF(File file) throws InvalidPasswordException, IOException
        {
            return this.loadPDF(FileUtils.readFileToByteArray(file));
        }

        @Override
        public PDFBuilder createEmptyPDF()
        {
            this.document = new PDDocument();
            return this.newPDFBuilderWithPage();
        }

        private PDFBuilderWithPage newPDFBuilderWithPage()
        {
            return new PDFBuilderWithPage()
            {
                private static final int PAGE_WIDTH = 540;

                private PDPage       page;
                private int          rowOffset       = 0;
                private int          footerOffset    = 0;
                private int          column          = 0;
                private IntSupplier  numberOfColumns = () -> this.columnWeights.size();
                private List<Double> columnWeights   = Arrays.asList(1.0);

                private List<PDDocument> addedSourceDocuments = new ArrayList<>();
                private int              addedPNGImageCounter = 0;

                private List<Consumer<PDFBuilderWithPage>> pageBreakListeners = new ArrayList<>();

                private PdfFont   font      = PdfFont.HELVETICA_BOLD;
                private TextColor textColor = TextColor.BLACK;;

                @Override
                public PDFBuilderWithPage addBlankPage()
                {
                    return this.addBlankPage(new PDPage());
                }

                private PDFBuilderWithPage addBlankPage(PDPage page)
                {
                    this.page = page;
                    this.resetTextOffsets();
                    this.executePageBreakListeners();
                    PDFLoaderImpl.this.document.addPage(this.page);
                    return this;
                }

                @Override
                public PDFBuilderWithPage addBlankPage(ResolutionProvider displayResolution)
                {
                    return this.addBlankPage(new PDPage(new PDRectangle(displayResolution.getWidth(), displayResolution.getHeight())));
                }

                private void executePageBreakListeners()
                {
                    this.pageBreakListeners.forEach(listener -> listener.accept(this));
                }

                private void resetTextOffsets()
                {
                    this.resetTextLineOffsets();
                    this.column = 0;
                }

                private void resetTextLineOffsets()
                {
                    this.rowOffset = 760;
                    this.footerOffset = 0;
                }

                @Override
                public PDFBuilderWithPage addPageBreakListener(Consumer<PDFBuilderWithPage> listener)
                {
                    this.pageBreakListeners.add(listener);
                    return this;
                }

                @Override
                public PDFBuilderWithPage getPage(int pageIndex)
                {
                    this.page = PDFLoaderImpl.this.document.getPage(pageIndex);

                    int height = this.determinePageHeight();
                    this.rowOffset = height - 50;
                    return this;
                }

                @Override
                public PDFBuilderWithPage getLastPage()
                {
                    this.resetTextOffsets();
                    return this.getPage(PDFLoaderImpl.this.document.getPages()
                                                                   .getCount()
                            - 1);
                }

                private int determinePageHeight()
                {
                    PDRectangle rectangle = this.page.getBBox();
                    int height = (int) rectangle.getHeight();
                    return height;
                }

                @Override
                public PDFWriter build()
                {
                    PDFWriter retval = null;
                    try
                    {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        PDFLoaderImpl.this.document.save(outputStream);
                        PDFLoaderImpl.this.document.close();
                        outputStream.close();

                        this.closeFurtherDocuments();

                        byte[] data = outputStream.toByteArray();
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                        retval = new PDFWriter()
                        {
                            @Override
                            public InputStream get()
                            {
                                return inputStream;
                            }

                            @Override
                            public byte[] getAsByteArray()
                            {
                                return data;
                            }

                            @Override
                            public Stream<String> getAsTextLines()
                            {
                                return StringUtils.splitToStreamByLineSeparator(this.getAsText());
                            }

                            @Override
                            public String getAsText()
                            {
                                try
                                {
                                    PDFTextStripper stripper = new PDFTextStripper();
                                    stripper.setAddMoreFormatting(false);
                                    //                                    stripper.setWordSeparator("\t");
                                    //                                        stripper.setSpacingTolerance(0.5f);
                                    //                                        stripper.setSortByPosition(true);
                                    PDDocument pdDocument = PDDocument.load(data);
                                    pdDocument.setAllSecurityToBeRemoved(true);

                                    return stripper.getText(pdDocument);
                                }
                                catch (IOException e)
                                {
                                    throw new IllegalStateException(e);
                                }
                            }

                            @Override
                            public void writeTo(File pdfFile) throws IOException
                            {
                                FileUtils.copyInputStreamToFile(inputStream, pdfFile);
                            }

                            @Override
                            public void writeSilentlyTo(File pdfFile)
                            {
                                SimpleExceptionHandler handler = null;
                                this.writeTo(pdfFile, handler);
                            }

                            @Override
                            public void writeTo(File pdfFile, SimpleExceptionHandler handler)
                            {
                                try
                                {
                                    this.writeTo(pdfFile);
                                }
                                catch (IOException e)
                                {
                                    if (handler != null)
                                    {
                                        handler.handle(e);
                                    }
                                }

                            }
                        };
                    }
                    catch (Exception e)
                    {
                        LOG.error("Exception during pdf creation", e);
                    }
                    return retval;
                }

                private void closeFurtherDocuments()
                {
                    this.addedSourceDocuments.forEach(furtherDocumentSource ->
                    {
                        try
                        {
                            furtherDocumentSource.close();
                        }
                        catch (IOException e)
                        {
                        }
                    });
                }

                @Override
                public PDFBuilderWithPage addText(String text)
                {
                    return this.addText(TextSize.NORMAL, text);
                }

                @Override
                public PDFBuilderWithPage addText(TextSizeProvider textSize, String text)
                {
                    int fontSize = textSize.getSize();

                    this.addText(text, fontSize, 0);

                    return this;
                }

                @Override
                public PDFBuilderWithPage addText(Consumer<TextOptions> textOptionsConsumer, String... texts)
                {
                    TextOptions textOptions = new TextOptions();
                    textOptionsConsumer.accept(textOptions);
                    int fontSize = textOptions.getTextSizeProvider()
                                              .orElse(TextSize.NORMAL)
                                              .getSize();
                    TextColor textColor = textOptions.getTextColor()
                                                     .orElse(this.textColor);

                    Optional.ofNullable(texts)
                            .map(Arrays::asList)
                            .orElse(Collections.emptyList())
                            .forEach(text -> this.addText(text, fontSize, textColor, 0));

                    return this;
                }

                private void addText(String text, int fontSize, double padding)
                {
                    this.addText(text, fontSize, this.textColor, padding);
                }

                private void addText(String text, int fontSize, TextColor textColor, double padding)
                {
                    this.addElementWithOffset(fontSize, padding, (rowOffset, columnOffset) ->
                    {
                        this.addRawText(text, fontSize, textColor, this.rowOffset, columnOffset);
                    });

                }

                private void addElementWithOffset(double paddingBefore, double paddingAfter, BiConsumer<Integer, Integer> offsetConsumer)
                {
                    int columnOffset = this.determineColumnOffset();

                    if (this.column == 0)
                    {
                        this.rowOffset -= paddingBefore * 1.5;
                    }

                    offsetConsumer.accept(this.rowOffset, columnOffset);

                    boolean isLastColumn = this.column >= this.numberOfColumns.getAsInt() - 1;
                    if (isLastColumn)
                    {
                        this.rowOffset -= paddingAfter;
                        this.switchToNewPageIfOffsetIsBelowThreshold();
                    }

                    if (isLastColumn)
                    {
                        this.column = 0;
                    }
                    else
                    {
                        this.column++;
                    }

                }

                private void switchToNewPageIfOffsetIsBelowThreshold()
                {
                    if (this.rowOffset < 60)
                    {
                        this.addBlankPage();
                    }
                }

                @Override
                public PDFBuilderWithPage addPNG(byte[] data, int width, int height)
                {
                    String imageName = this.generatePNGImageName();
                    return this.addPNG(data, imageName, width, height);
                }

                @Override
                public PDFBuilderWithPage addPNG(byte[] data, ResolutionProvider displayResolution)
                {
                    return this.addPNG(data, displayResolution.getWidth(), displayResolution.getHeight());
                }

                private String generatePNGImageName()
                {
                    return "Image" + ++this.addedPNGImageCounter;
                }

                @Override
                public PDFBuilderWithPage addPNG(byte[] data, String imageName, int width, int height)
                {
                    double paddingBefore = height;
                    double paddingAfter = 6;
                    this.addElementWithOffset(paddingBefore, paddingAfter, (rowOffset, columnOffset) ->
                    {
                        this.addPNG(data, imageName, rowOffset, columnOffset, width, height);
                    });

                    return this;
                }

                @Override
                public PDFBuilderWithPage addPageWithPNG(byte[] png, ResolutionProvider displayResolution)
                {
                    return this.addBlankPage(displayResolution)
                               .addPNGAsBackground(png);
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data)
                {
                    return this.addPNGAsBackground(data, this.generatePNGImageName());
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data, ResolutionProvider displayResolution)
                {
                    return this.addPNGAsBackground(data, this.generatePNGImageName(), displayResolution);
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, ResolutionProvider displayResolution)
                {
                    int width = displayResolution.getWidth();
                    int height = displayResolution.getHeight();
                    return this.addPNGAsBackground(data, imageName, width, height);
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName)
                {
                    if (data != null && data.length > 0)
                    {
                        PDRectangle box = this.page.getBBox();
                        int width = (int) box.getWidth();
                        int height = (int) box.getHeight();
                        this.addPNGAsBackground(data, imageName, width, height);
                    }

                    return this;
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, int width, int height)
                {
                    return this.addPNG(data, imageName, 0, 0, width, height);
                }

                @Override
                public PDFBuilderWithPage addPNGAsBackground(byte[] data, String imageName, double leftAsRatio, double topAsRatio, int width, int height)
                {
                    int rowOffset = (int) Math.round(-0.5 * height + (1.0 - topAsRatio) * this.determinePageHeight());
                    int columnOffset = (int) Math.round(leftAsRatio * PAGE_WIDTH);
                    return this.addPNG(data, imageName, rowOffset, columnOffset, width, height);
                }

                private PDFBuilderWithPage addPNG(byte[] data, String imageName, int rowOffset, int columnOffset, int width, int height)
                {
                    try (PDPageContentStream contentStream = new PDPageContentStream(PDFLoaderImpl.this.document, this.page,
                                                                                     PDPageContentStream.AppendMode.PREPEND, true, true))
                    {
                        PDImageXObject imageObject = PDImageXObject.createFromByteArray(PDFLoaderImpl.this.document, data, imageName);
                        contentStream.drawImage(imageObject, columnOffset, rowOffset, width, height);
                        contentStream.close();
                    }
                    catch (IOException e)
                    {
                        LOG.error("Exception defining text", e);
                    }

                    return this;
                }

                @Override
                public PDFBuilderWithPage withColumns(int numberOfColumns, Consumer<PDFBuilderWithPage> columnBuilderConsumer)
                {
                    return this.withColumns(numberOfColumns, column -> 1.0, columnBuilderConsumer);
                }

                @Override
                public PDFBuilderWithPage withColumns(int numberOfColumns, IntToDoubleFunction columnWeightFunction,
                                                      Consumer<PDFBuilderWithPage> columnBuilderConsumer)
                {
                    List<Double> previousColumnWeigths = this.columnWeights;
                    this.columnWeights = IntStream.range(0, numberOfColumns)
                                                  .mapToDouble(columnWeightFunction)
                                                  .boxed()
                                                  .collect(Collectors.toList());

                    columnBuilderConsumer.accept(this);

                    this.columnWeights = previousColumnWeigths;
                    this.column = 0;
                    return this;
                }

                @Override
                public PDFBuilderWithPage withColumns(List<Double> columnWeights, Consumer<PDFBuilderWithPage> columnBuilderConsumer)
                {
                    return this.withColumns(columnWeights.size(), column -> columnWeights.get(column), columnBuilderConsumer);
                }

                private void addRawText(String text, int fontSize, int offset)
                {
                    int columnOffset = this.determineColumnOffset();
                    this.addRawText(text, fontSize, this.textColor, offset, columnOffset);
                }

                private void addRawText(String text, int fontSize, TextColor textColor, int rowOffset, int columnOffset)
                {
                    PDFont effectiveFont = Optional.ofNullable(this.font)
                                                   .map(PdfFont::getRawFont)
                                                   .orElse(PDType1Font.HELVETICA_BOLD);

                    try (PDPageContentStream contents = new PDPageContentStream(PDFLoaderImpl.this.document, this.page, PDPageContentStream.AppendMode.PREPEND,
                                                                                true, true))
                    {
                        contents.setLeading(1.5);
                        contents.beginText();
                        try
                        {
                            contents.setNonStrokingColor(textColor.asAwtColor());
                            contents.setFont(effectiveFont, fontSize);
                            contents.newLineAtOffset(columnOffset, rowOffset);
                            contents.showText(text);
                        }
                        finally
                        {
                            contents.endText();
                        }
                    }
                    catch (Exception e)
                    {
                        Optional.ofNullable(PDFLoaderImpl.this.exceptionHandler)
                                .ifPresent(consumer -> consumer.accept(e));
                    }
                }

                private int determineColumnOffset()
                {
                    double allColumnWeigth = this.columnWeights.stream()
                                                               .mapToDouble(MapperUtils.identitiyForDoubleAsUnboxed())
                                                               .sum();
                    double previousColumnsWeigth = this.columnWeights.stream()
                                                                     .mapToDouble(MapperUtils.identitiyForDoubleAsUnboxed())
                                                                     .limit(this.column)
                                                                     .sum();
                    double ratio = previousColumnsWeigth / allColumnWeigth;
                    return PAGE_WIDTH / 10 + (int) Math.round(ratio * PAGE_WIDTH);
                }

                @Override
                public PDFBuilderWithPage addText(Iterable<String> texts)
                {
                    if (texts != null)
                    {
                        texts.forEach(this::addText);
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addText(String... texts)
                {
                    if (texts != null)
                    {
                        this.addText(Arrays.asList(texts));
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addText(TextSizeProvider textSize, Iterable<String> texts)
                {
                    if (texts != null)
                    {
                        texts.forEach(text -> this.addText(textSize, text));
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addText(TextSizeProvider textSize, String... texts)
                {
                    if (texts != null)
                    {
                        this.addText(textSize, Arrays.asList(texts));
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addBlankTextLine()
                {
                    return this.addBlankTextLine(TextSize.NORMAL);

                }

                @Override
                public PDFBuilderWithPage addBlankTextLine(TextSizeProvider textSize)
                {
                    return this.addText(textSize, "");
                }

                @Override
                public PDFBuilderWithPage addBlankTextLines(TextSizeProvider textSize, int numberOfLines)
                {
                    IntStream.range(0, numberOfLines)
                             .forEach(i -> this.addBlankTextLine(textSize));
                    return this;
                }

                @Override
                public PDFBuilderWithPage gotoLine(TextSizeProvider textSize, int lineNumber)
                {
                    this.resetTextLineOffsets();
                    return this.addBlankTextLines(textSize, lineNumber);
                }

                @Override
                public PDFBuilderWithPage addTitle(String title)
                {
                    this.addText(title, 24, 3);
                    return this;
                }

                @Override
                public PDFBuilderWithPage addSubTitle(String subTitle)
                {
                    this.addText(subTitle, 8, 6);
                    return this;
                }

                @Override
                public PDFBuilderWithPage addFooter(String footer)
                {
                    int offset = 40 - this.footerOffset;
                    int fontSize = 6;
                    this.addRawText(footer, fontSize, offset);

                    this.footerOffset += fontSize * 1.5;

                    return this;
                }

                @Override
                public PDFBuilderWithPage addFooter(String... footers)
                {
                    if (footers != null)
                    {
                        for (String footer : footers)
                        {
                            this.addFooter(footer);
                        }
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage ifWithRatioOfPageLeft(DoublePredicate ratioFilter, Consumer<PDFBuilderWithPage> pageConsumer)
                {
                    int pageHeight = this.determinePageHeight();
                    double ratio = this.rowOffset / (1.0 * pageHeight);
                    if (ratioFilter != null && pageConsumer != null && ratioFilter.test(ratio))
                    {
                        pageConsumer.accept(this);
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addPagesOfFurtherPDF(byte[] pdf) throws InvalidPasswordException, IOException
                {
                    PDDocument furtherDocument = PDDocument.load(pdf);

                    furtherDocument.getPages()
                                   .forEach(page ->
                                   {
                                       PDFLoaderImpl.this.document.addPage(page);
                                   });

                    this.addedSourceDocuments.add(furtherDocument);

                    return this.getLastPage();
                }

                @Override
                public PDFBuilderWithPage addPagesOfFurtherPDFSilently(Collection<byte[]> pdfs)
                {
                    if (pdfs != null)
                    {
                        pdfs.forEach(pdf -> this.addPagesOfFurtherPDFSilently(pdf));
                    }
                    return this;
                }

                @Override
                public PDFBuilderWithPage addPagesOfFurtherPDFSilently(byte[] pdf)
                {
                    SimpleExceptionHandler exceptionHandler = null;
                    this.addPagesOfFurtherPDF(pdf, exceptionHandler);
                    return this;
                }

                @Override
                public PDFBuilderWithPage addPagesOfFurtherPDF(byte[] pdf, SimpleExceptionHandler exceptionHandler)
                {
                    try
                    {
                        this.addPagesOfFurtherPDF(pdf);
                    }
                    catch (Exception e)
                    {
                        if (exceptionHandler != null)
                        {
                            exceptionHandler.handle(e);
                        }
                    }
                    return this;
                }

                @Override
                public PDFBuilder processPages(PagesProcessor processor)
                {
                    processor.process(IntStream.range(0, PDFLoaderImpl.this.document.getNumberOfPages())

                                               .mapToObj(pageIndex ->
                                               {
                                                   this.resetTextOffsets();
                                                   return this.getPage(pageIndex);
                                               }));
                    return this;
                }

                @Override
                public PDFBuilder forEachPage(PageProcessor processor)
                {
                    return this.processPages((Stream<PDFBuilderWithPage> pages) -> pages.forEach(page -> processor.accept(page)));
                }

                @Override
                public <E> PDFBuilderWithPage withElements(Stream<E> elements, ElementProcessor<E> processor)
                {
                    if (elements != null && processor != null)
                    {
                        elements.forEach(element -> processor.handle(this, element));
                    }
                    return this;
                }

                @Override
                public <E> PDFBuilderWithPage withElements(Collection<E> elements, ElementProcessor<E> processor)
                {
                    return this.withElements(elements.stream(), processor);
                }

                @Override
                public <E> PDFBuilderWithPage withRawElements(Collection<E> elements, RawElementProcessor<E> processor)
                {
                    return this.withElements(elements, (page, element) -> processor.handle(element));
                }

                @Override
                public PDFBuilderWithPage withFont(PdfFont pdfFont)
                {
                    this.font = pdfFont;
                    return this;
                }

            };
        }

        @Override
        public PDFLoader useExceptionHandler(ExceptionHandler exceptionHandler)
        {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

    }

    public static PDFLoader getPDFInstance()
    {
        return new PDFLoaderImpl();
    }

    public static InputStream renderPDFToImage(InputStream inputStream)
    {
        InputStream retval = null;
        try
        {
            //
            BufferedImage bufferedImage;
            {
                PDDocument document = PDDocument.load(inputStream);
                PDFRenderer renderer = new PDFRenderer(document);
                bufferedImage = renderer.renderImage(0);
                document.close();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "JPG", bos);
            bos.close();
            retval = new ByteArrayInputStream(bos.toByteArray());
        }
        catch (Exception e)
        {
            LOG.error("Exception during pdf to image rendering", e);
        }
        return retval;
    }

    @SuppressWarnings("deprecation")
    public static InputStream merge(Stream<InputStream> inputStreams)
    {
        PDFMergerUtility merger = new PDFMergerUtility();

        inputStreams.forEach(source -> merger.addSource(source));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        merger.setDestinationStream(byteArrayOutputStream);
        try
        {
            merger.mergeDocuments();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    public static class TextOptions
    {
        private TextSizeProvider textSizeProvider;
        private TextColor        textColor;

        public Optional<TextSizeProvider> getTextSizeProvider()
        {
            return Optional.ofNullable(this.textSizeProvider);
        }

        public TextOptions withTextSize(TextSizeProvider textSize)
        {
            this.textSizeProvider = textSize;
            return this;
        }

        public Optional<TextColor> getTextColor()
        {
            return Optional.ofNullable(this.textColor);
        }

        public TextOptions withTextColor(TextColor textColor)
        {
            this.textColor = textColor;
            return this;
        }

        @Override
        public String toString()
        {
            return "TextOptions [textSizeProvider=" + this.textSizeProvider + ", textColor=" + this.textColor + "]";
        }

    }

    public enum TextColor
    {
        BLACK(Color.BLACK), WHITE(Color.WHITE), RED(Color.RED), BLUE(Color.BLUE), MAGENTA(Color.MAGENTA), YELLOW(Color.YELLOW), GREEN(Color.GREEN);

        private Color awtColor;

        private TextColor(Color awtColor)
        {
            this.awtColor = awtColor;

        }

        public Color asAwtColor()
        {
            return this.awtColor;
        }
    }

    public static enum PdfFont
    {
        TIMES_ROMAN(PDType1Font.TIMES_ROMAN),
        TIMES_BOLD(PDType1Font.TIMES_BOLD),
        TIMES_ITALIC(PDType1Font.TIMES_ITALIC),
        TIMES_BOLD_ITALIC(PDType1Font.TIMES_BOLD_ITALIC),
        HELVETICA(PDType1Font.HELVETICA),
        HELVETICA_BOLD(PDType1Font.HELVETICA_BOLD),
        HELVETICA_OBLIQUE(PDType1Font.HELVETICA_OBLIQUE),
        HELVETICA_BOLD_OBLIQUE(PDType1Font.HELVETICA_BOLD_OBLIQUE),
        COURIER(PDType1Font.COURIER),
        COURIER_BOLD(PDType1Font.COURIER_BOLD),
        COURIER_OBLIQUE(PDType1Font.COURIER_OBLIQUE),
        COURIER_BOLD_OBLIQUE(PDType1Font.COURIER_BOLD_OBLIQUE),
        SYMBOL(PDType1Font.SYMBOL),
        ZAPF_DINGBATS(PDType1Font.ZAPF_DINGBATS);

        private PDType1Font rawFont;

        private PdfFont(PDType1Font rawFont)
        {
            this.rawFont = rawFont;
        }

        protected PDType1Font getRawFont()
        {
            return this.rawFont;
        }

    }
}
