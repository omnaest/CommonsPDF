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
package org.omnaest.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.omnaest.pdf.PDFUtils.PDFWriter;
import org.omnaest.pdf.PDFUtils.TextSize;

/**
 * Round trip tests for {@link PDFUtils}: a document is written and read back as text, without relying on any external file.
 *
 * @see PDFUtils
 * @author omnaest
 */
public class PDFUtilsRoundTripTest
{
    private static final String TITLE       = "Report Title";
    private static final String FIRST_LINE  = "First body line";
    private static final String SECOND_LINE = "Second body line";
    private static final String FOOTER      = "Footer text";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PDFWriter createTestDocument()
    {
        return PDFUtils.getPDFInstance()
                       .createEmptyPDF()
                       .addBlankPage()
                       .addTitle(TITLE)
                       .addText(FIRST_LINE)
                       .addText(TextSize.SMALL, SECOND_LINE)
                       .addFooter(FOOTER)
                       .build();
    }

    @Test
    public void testTextRoundTrip() throws Exception
    {
        String text = PDFUtils.getPDFInstance()
                              .loadPDF(this.createTestDocument()
                                           .getAsByteArray())
                              .build()
                              .getAsText();

        assertTrue("title missing: " + text, text.contains(TITLE));
        assertTrue("first line missing: " + text, text.contains(FIRST_LINE));
        assertTrue("second line missing: " + text, text.contains(SECOND_LINE));
        assertTrue("footer missing: " + text, text.contains(FOOTER));
    }

    /**
     * The extracted text has to follow the visual top down order of the page, not the order of the underlying content stream.
     */
    @Test
    public void testTextRoundTripRetainsVisualOrder() throws Exception
    {
        String text = PDFUtils.getPDFInstance()
                              .loadPDF(this.createTestDocument()
                                           .getAsByteArray())
                              .build()
                              .getAsText();

        assertTrue("title after first line: " + text, text.indexOf(TITLE) < text.indexOf(FIRST_LINE));
        assertTrue("first line after second line: " + text, text.indexOf(FIRST_LINE) < text.indexOf(SECOND_LINE));
        assertTrue("second line after footer: " + text, text.indexOf(SECOND_LINE) < text.indexOf(FOOTER));
    }

    @Test
    public void testGetAsTextLines() throws Exception
    {
        List<String> lines = PDFUtils.getPDFInstance()
                                     .loadPDF(this.createTestDocument()
                                                  .getAsByteArray())
                                     .build()
                                     .getAsTextLines()
                                     .filter(line -> !line.trim()
                                                          .isEmpty())
                                     .collect(Collectors.toList());

        assertEquals(java.util.Arrays.asList(TITLE, FIRST_LINE, SECOND_LINE, FOOTER), lines);
    }

    /**
     * A {@link PDFWriter} has to stay usable after a first read, so repeated {@link PDFWriter#writeTo(File)} calls yield the same content.
     */
    @Test
    public void testWriterIsRepeatable() throws Exception
    {
        PDFWriter writer = this.createTestDocument();

        File firstFile = this.temporaryFolder.newFile("first.pdf");
        File secondFile = this.temporaryFolder.newFile("second.pdf");
        writer.writeTo(firstFile);
        writer.writeTo(secondFile);

        byte[] expected = writer.getAsByteArray();
        assertTrue("first write is empty", firstFile.length() > 0);
        assertEquals(expected.length, firstFile.length());
        assertEquals(expected.length, secondFile.length());
        assertArrayEquals(org.apache.commons.io.FileUtils.readFileToByteArray(firstFile),
                          org.apache.commons.io.FileUtils.readFileToByteArray(secondFile));
    }

    /**
     * {@link PDFWriter#get()} has to return a fresh {@link java.io.InputStream} for each call.
     */
    @Test
    public void testGetReturnsFreshInputStream() throws Exception
    {
        PDFWriter writer = this.createTestDocument();

        byte[] first = org.apache.commons.io.IOUtils.toByteArray(writer.get());
        byte[] second = org.apache.commons.io.IOUtils.toByteArray(writer.get());

        assertTrue("first read is empty", first.length > 0);
        assertArrayEquals(first, second);
        assertArrayEquals(writer.getAsByteArray(), first);
    }

    @Test
    public void testGetAsByteArrayContainer() throws Exception
    {
        PDFWriter writer = this.createTestDocument();

        assertArrayEquals(writer.getAsByteArray(), writer.getAsByteArrayContainer()
                                                         .toByteArray());
        assertArrayEquals(writer.getAsByteArray(), org.apache.commons.io.IOUtils.toByteArray(writer.getAsByteArrayContainer()
                                                                                                   .toInputStream()));
    }
}
