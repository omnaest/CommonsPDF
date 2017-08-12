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

	public static interface PDFBuilderWithPage extends PDFBuilder
	{
		PDFBuilderWithPage addText(String text);

		PDFBuilderWithPage addTitle(String title);

		PDFBuilderWithPage addSubTitle(String subTitle);

		@Override
		PDFBuilderWithPage getPage(int pageIndex);
	}

	public static interface PDFBuilder
	{

		PDFWriter build();

		PDFBuilderWithPage addBlankPage();

		PDFBuilderWithPage getPage(int pageIndex);

	}

	public static interface PDFWriter
	{

		void write(File pdfFile) throws IOException;

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

						PDRectangle rectangle = this.page.getBBox();

						this.offset = (int) (rectangle.getHeight() - 50);
						return this;
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
							};
						} catch (Exception e)
						{
							LOG.error("Exception during pdf creation", e);
						}
						return retval;
					}

					@Override
					public PDFBuilderWithPage addText(String text)
					{
						int fontSize = 12;

						this.addText(text, fontSize, 0);

						return this;
					}

					private void addText(String text, int fontSize, double padding)
					{
						PDFont font = PDType1Font.HELVETICA_BOLD;

						try (PDPageContentStream contents = new PDPageContentStream(document, this.page, PDPageContentStream.AppendMode.PREPEND, true, true))
						{
							this.offset -= fontSize * 1.5;

							contents.setLeading(1.5);
							contents.beginText();
							contents.setFont(font, fontSize);
							contents.newLineAtOffset(60, this.offset);
							contents.showText(text);
							contents.endText();

							this.offset -= padding;

							if (this.offset < 60)
							{
								this.addBlankPage();
							}
						} catch (IOException e)
						{
							LOG.error("", e);
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
