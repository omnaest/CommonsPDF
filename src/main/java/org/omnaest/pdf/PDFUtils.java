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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.omnaest.utils.SimpleExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PDFUtils
{
	private static Logger LOG = LoggerFactory.getLogger(PDFUtils.class);

	public static interface PDFLoader
	{

		PDFBuilder createEmptyPDF();

		PDFBuilder loadPDF(byte[] data) throws InvalidPasswordException, IOException;

	}

	public static interface TextSizeProvider
	{
		public int getSize();
	}

	public enum TextSize implements TextSizeProvider
	{
		LARGE(16), NORMAL(12), SMALL(8), VERY_SMALL(6);

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

	public static interface ElementProcessor<E>
	{
		public void handle(PDFBuilderWithPage page, E element);
	}

	public static interface PDFBuilderWithPage extends PDFBuilder
	{
		PDFBuilderWithPage addText(String text);

		PDFBuilderWithPage addText(TextSizeProvider textSize, String text);

		PDFBuilderWithPage addTitle(String title);

		PDFBuilderWithPage addSubTitle(String subTitle);

		@Override
		PDFBuilderWithPage getPage(int pageIndex);

		PDFBuilderWithPage getLastPage();

		PDFBuilderWithPage addFooter(String footer);

		<E> PDFBuilderWithPage withElements(Stream<E> elements, ElementProcessor<E> processor);

	}

	public static interface PageProcessor
	{
		public void process(Stream<PDFBuilderWithPage> pages);
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

		PDFBuilderWithPage getPage(int pageIndex);

		PDFBuilder addPagesOfFurtherPDF(byte[] pdf) throws InvalidPasswordException, IOException;

		PDFBuilder addPagesOfFurtherPDF(byte[] pdf, SimpleExceptionHandler exceptionHandler);

		PDFBuilder addPagesOfFurtherPDFSilently(byte[] pdf);

		PDFBuilder addPagesOfFurtherPDFSilently(Collection<byte[]> pdfs);

		PDFBuilder processPages(PageProcessor processor);

	}

	public static interface PDFWriter
	{
		/**
		 * Writing the pdf to {@link File}
		 *
		 * @param pdfFile
		 * @throws IOException
		 */
		void write(File pdfFile) throws IOException;

		/**
		 * Similar to {@link #write(File)} but using the given {@link SimpleExceptionHandler} for handling {@link Exception}s
		 *
		 * @param pdfFile
		 * @param handler
		 */
		void write(File pdfFile, SimpleExceptionHandler handler);

		/**
		 * Similar to {@link #write(File)} without throwing an {@link IOException}
		 *
		 * @param pdfFile
		 */
		void writeSilently(File pdfFile);

		InputStream get();

		byte[] getAsByteArray();

	}

	public static PDFLoader getPDFInstance()
	{
		return new PDFLoader()
		{
			private PDDocument document = null;

			@Override
			public PDFBuilder loadPDF(byte[] data) throws InvalidPasswordException, IOException
			{
				this.document = PDDocument.load(data);
				return this.newPDFBuilderWithPage();
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
					private PDPage	page;
					private int		offset	= 0;

					private List<PDDocument> addedSourceDocuments = new ArrayList<>();

					@Override
					public PDFBuilderWithPage addBlankPage()
					{
						this.page = new PDPage();
						this.offset = 760;
						document.addPage(this.page);
						return this;
					}

					@Override
					public PDFBuilderWithPage getPage(int pageIndex)
					{
						this.page = document.getPage(pageIndex);

						int height = this.determinePageHeight();
						this.offset = height - 50;
						return this;
					}

					@Override
					public PDFBuilderWithPage getLastPage()
					{
						return this.getPage(document.getPages()
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
							document.save(outputStream);
							document.close();
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
								public void write(File pdfFile) throws IOException
								{
									FileUtils.copyInputStreamToFile(inputStream, pdfFile);
								}

								@Override
								public void writeSilently(File pdfFile)
								{
									SimpleExceptionHandler handler = null;
									this.write(pdfFile, handler);
								}

								@Override
								public void write(File pdfFile, SimpleExceptionHandler handler)
								{
									try
									{
										this.write(pdfFile);
									} catch (IOException e)
									{
										if (handler != null)
										{
											handler.handle(e);
										}
									}

								}
							};
						} catch (Exception e)
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
							} catch (IOException e)
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

					private void addText(String text, int fontSize, double padding)
					{
						this.offset -= fontSize * 1.5;
						this.addRawText(text, fontSize, this.offset);
						this.offset -= padding;
						if (this.offset < 60)
						{
							this.addBlankPage();
						}
					}

					private void addRawText(String text, int fontSize, int offset)
					{
						PDFont font = PDType1Font.HELVETICA_BOLD;

						try (PDPageContentStream contents = new PDPageContentStream(document, this.page, PDPageContentStream.AppendMode.PREPEND, true, true))
						{
							contents.setLeading(1.5);
							contents.beginText();
							contents.setFont(font, fontSize);
							contents.newLineAtOffset(60, offset);
							contents.showText(text);
							contents.endText();

						} catch (IOException e)
						{
							LOG.error("Exception defining text", e);
						}
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
						int offset = 50;
						int fontSize = 6;
						this.addRawText(footer, fontSize, offset);
						return this;
					}

					@Override
					public PDFBuilder addPagesOfFurtherPDF(byte[] pdf) throws InvalidPasswordException, IOException
					{
						PDDocument furtherDocument = PDDocument.load(pdf);

						furtherDocument	.getPages()
										.forEach(page ->
										{
											document.addPage(page);
										});

						this.addedSourceDocuments.add(furtherDocument);

						return this;
					}

					@Override
					public PDFBuilder addPagesOfFurtherPDFSilently(Collection<byte[]> pdfs)
					{
						if (pdfs != null)
						{
							pdfs.forEach(pdf -> this.addPagesOfFurtherPDFSilently(pdf));
						}
						return this;
					}

					@Override
					public PDFBuilder addPagesOfFurtherPDFSilently(byte[] pdf)
					{
						SimpleExceptionHandler exceptionHandler = null;
						this.addPagesOfFurtherPDF(pdf, exceptionHandler);
						return this;
					}

					@Override
					public PDFBuilder addPagesOfFurtherPDF(byte[] pdf, SimpleExceptionHandler exceptionHandler)
					{
						try
						{
							this.addPagesOfFurtherPDF(pdf);
						} catch (Exception e)
						{
							if (exceptionHandler != null)
							{
								exceptionHandler.handle(e);
							}
						}
						return this;
					}

					@Override
					public PDFBuilder processPages(PageProcessor processor)
					{
						processor.process(IntStream	.range(0, document.getNumberOfPages())

													.mapToObj(pageIndex -> this.getPage(pageIndex)));
						return this;
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
				};
			}
		};
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
		} catch (Exception e)
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
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
	}
}
