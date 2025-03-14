/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.modules.extractor;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

public class ExtractorHTMLTest extends StringExtractorTestBase {

    
    final public static String[] VALID_TEST_DATA = new String[] {
        "<a href=\"http://www.slashdot.org\">yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'>yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org>yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href=\"http://www.slashdot.org\">yellow journalism A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=\"http://www.slashdot.org\"/>yellow journalism A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'/>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org/>yellow journalism A",
        "http://www.slashdot.org",

        "<img src=\"foo.gif\"> IMG",
        "http://www.archive.org/start/foo.gif",

    };
    
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }

    @Override
    protected Extractor makeExtractor() {
        ExtractorHTML result = new ExtractorHTML();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        result.setLoggerModule(ulm);
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        result.setMetadata(metadata);
        result.setExtractorJS(new ExtractorJS());
        result.afterPropertiesSet();
        return result;
    }
    
    protected ExtractorHTML getExtractor() {
        return (ExtractorHTML) extractor;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/start/");
        CrawlURI euri = new CrawlURI(src, null, null, 
                LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content, "UTF-8");
        euri.setContentType("text/html");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        LinkContext context = determineContext(content);
        Hop hop = determineHop(content);
        CrawlURI link = euri.createCrawlURI(dest, context, hop);
        result.add(new TestData(euri, link));
        
        euri = new CrawlURI(src, null, null, LinkContext.NAVLINK_MISC);
        recorder = createRecorder(content, "UTF-8");
        euri.setContentType("application/xhtml");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
        result.add(new TestData(euri, link));
        
        return result;
    }

    
    private static Hop determineHop(String s) {
        if (s.endsWith(" IMG")) {
            return Hop.EMBED;
        }
        return Hop.NAVLINK;
    }
    
    
    private static LinkContext determineContext(String s) {
        if (s.endsWith(" A")) {
            return HTMLLinkContext.get("a/@href");
        }
        if (s.endsWith(" IMG")) {
            return HTMLLinkContext.get("img/@src");
        }
        return LinkContext.NAVLINK_MISC;
    }

    /**
     * Test a missing whitespace issue found in form
     * 
     * [HER-1128] ExtractorHTML fails to extract FRAME SRC link without
     * whitespace before SRC http://webteam.archive.org/jira/browse/HER-1128
     */
    public void testNoWhitespaceBeforeValidAttribute() throws URIException {
        expectSingleLink(
                "http://expected.example.com/",
                "<frame name=\"main\"src=\"http://expected.example.com/\"> ");
    }
    
    /**
     * Expect the extractor to find the single given URI in the supplied
     * source material. Fail if that one lik is not found. 
     * 
     * TODO: expand to capture expected Link instance characteristics 
     * (source, hop, context, etc?)
     * 
     * @param expected String target URI that should be extracted
     * @param source CharSequence source material to extract
     * @throws URIException
     */
    protected void expectSingleLink(String expected, CharSequence source) throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        getExtractor().extract(puri, source);
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        assertTrue("did not find single link",links.length==1);
        assertTrue("expected link not found", 
                links[0].getURI().equals(expected));
    }
    
    /**
     * Test only extract FORM ACTIONS with METHOD GET 
     * 
     * [HER-1280] do not by default GET form action URLs declared as POST, 
     * because it can cause problems/complaints 
     * http://webteam.archive.org/jira/browse/HER-1280
     */
    public void testOnlyExtractFormGets() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "<form method=\"get\" action=\"http://www.example.com/ok1\"> "+
            "<form action=\"http://www.example.com/ok2\" method=\"get\"> "+
            "<form method=\"post\" action=\"http://www.example.com/notok\"> "+
            "<form action=\"http://www.example.com/ok3\"> ";
        getExtractor().extract(puri, cs);
        // find exactly 3 (not the POST) action URIs
        assertTrue("incorrect number of links found", puri.getOutLinks().size()==3);
    }

    /*
     * positive and negative tests for uris in meta tag's content attribute
     */
    public void testMetaContentURI() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
                "<meta property=\"og:video\" content=\"http://www.example.com/absolute.mp4\" /> "+
                "<meta property=\"og:video\" content=\"/relative.mp4\" /> "+
                "<meta property=\"og:video:height\" content=\"333\" />"+
                "<meta property=\"og:video:type\" content=\"video/mp4\" />"+
                "<meta property=\"strangeproperty\" content=\"notaurl\" meaninglessurl=\"http://www.example.com/shouldnotbeextracted.html\" />";
        
        getExtractor().extract(puri, cs);
        
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);         
        String dest1 = "http://www.example.com/absolute.mp4";
        String dest2 = "http://www.example.com/relative.mp4";
        
        assertTrue("incorrect number of links found", puri.getOutLinks().size()==2);
        assertEquals("expected uri in 'content' attribute of meta tag not found",dest1,
                links[0].getURI());        
        assertEquals("expected uri in 'content' attribute of meta tag not found",dest2,
                links[1].getURI());
    }
    
    /**
     * Test detection, respect of meta robots nofollow directive
     */
    public void testMetaRobots() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "Blah Blah "+
            "<meta name='robots' content='index,nofollow'>"+
            "<a href='blahblah'>blah</a> "+
            "blahblah";
        getExtractor().extract(puri, cs);
        assertEquals("meta robots content not extracted","index,nofollow",
                puri.getData().get(ExtractorHTML.A_META_ROBOTS));
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        assertTrue("link extracted despite meta robots",links.length==0);
    }
    
    /**
     * Test that relative URIs with late colons aren't misinterpreted
     * as absolute URIs with long, illegal scheme components. 
     * 
     * See http://webteam.archive.org/jira/browse/HER-1268
     * 
     * @throws URIException
     */
    public void testBadRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = "<a href=\"example.html;jsessionid=deadbeef:deadbeed?parameter=this:value\"/>"
                + "<a href=\"example.html?parameter=this:value\"/>";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI()
                        .indexOf(
                                "/example.html;jsessionid=deadbeef:deadbeed?parameter=this:value") >= 0;
            }
        }));

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().indexOf(
                        "/example.html?parameter=this:value") >= 0;
            }
        }));
    }

    public void testDataUrisAreIgnored() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com"));
        CharSequence cs = "<img src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='>";
        getExtractor().extract(curi, cs);
        assertEquals(0, curi.getOutLinks().size());
    }
    
    /**
     * Test that relative base href's are resolved correctly:
     *
     * See
     *
     * @throws URIException
     */
    public void testRelativeBaseHrefRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"/forum/\"/>\n" +
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI()
                        .indexOf(
                                ".de/forum/index.php/fa/89652/0/") >= 0;
            }
        }));
    }


    /**
     * Test that the first base href is used:
     *
     * See
     *
     * @throws URIException
     */
    public void testFirstBaseHrefRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"/first/\"/>\n" + "<base href=\"/forum/\"/>\n" +
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI()
                        .indexOf(
                                ".de/first/index.php/fa/89652/0/") >= 0;
            }
        }));
    }

    /**
     * Test that absolute base href's are resolved correctly:
     *
     * @throws URIException
     */
    public void testAbsoluteBaseHrefRelativeLinks() throws URIException {

        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"https://www.schmid-gartenpflanzen.de/forum/\"/>\n" +
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI()
                        .indexOf(
                                ".de/forum/index.php/fa/89652/0/") >= 0;
            }
        }));

    }

    /**
     * Test if scheme is maintained by speculative hops onto exact 
     * same host
     * 
     * [HER-1524] speculativeFixup in ExtractorJS should maintain URL scheme
     */
    public void testSpeculativeLinkExtraction() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.example.com"));
        CharSequence cs = 
            "<script type=\"text/javascript\">_parameter=\"www.anotherexample.com\";"
                + "_anotherparameter=\"www.example.com/index.html\""
                + ";</script>";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                System.err.println("comparing: "
                        + ((CrawlURI) object).getURI()
                        + " and https://www.anotherexample.com/");
                return ((CrawlURI) object).getURI().equals(
                        "http://www.anotherexample.com/");
            }
        }));
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().equals(
                        "https://www.example.com/index.html");
            }
        }));
    }
    
    
    /**
     * test to see if embedded <SCRIPT/> which writes script TYPE
     * creates any outlinks, e.g. "type='text/javascript'". 
     * 
     * [HER-1526] SCRIPT writing script TYPE common trigger of bogus links 
     *   (eg. 'text/javascript')
     *   
     * @throws URIException
     */
    public void testScriptTagWritingScriptType() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/en/fiche/dossier/322/"));
        CharSequence cs = 
            "<script type=\"text/javascript\">"
            + "var gaJsHost = ((\"https:\" == document.location.protocol) "
            + "? \"https://ssl.\" : \"http://www.\");"
            + " document.write(unescape(\"%3Cscript src='\" + gaJsHost + "
            + "\"google-analytics.com/ga.js' "
            + "type='text/javascript'%3E%3C/script%3E\"));"
            + "</script>";
        getExtractor().extract(curi, cs);
        assertEquals(Collections.EMPTY_SET, curi.getOutLinks());
    }

    public void testOutLinksWithBaseHref() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/abc/index.html"));
        CharSequence cs = 
            "<base href=\"http://www.example.com/\">" + 
            "<a href=\"def/another1.html\">" + 
            "<a href=\"ghi/another2.html\">";
        getExtractor().extract(puri, cs);
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links); 
        String dest1 = "http://www.example.com/def/another1.html";
        String dest2 = "http://www.example.com/ghi/another2.html";
        // ensure outlink from base href
        assertEquals("outlink1 from base href",dest1,
                links[1].getURI());
        assertEquals("outlink2 from base href",dest2,
                links[2].getURI());
    }
    
    protected Predicate destinationContainsPredicate(final String fragment) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().indexOf(fragment) >= 0;
            }
        };
    }
    
    protected Predicate destinationsIsPredicate(final String value) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().equals(value);
            }
        };
    }
    
    /**
     * HER-1728 
     * @throws URIException 
     */
    public void testFlashvarsParamValue() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
        CharSequence cs = 
            "<object classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" codebase=\"http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,28,0\" id=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "    <param name=\"flashvars\" value=\"zoomifyXMLPath=ParamZoomifySlideshowViewer.xml\">\n" + 
            "    <param name=\"menu\" value=\"false\">\n" + 
            "    <param name=\"bgcolor\" value=\"#000000\">\n" + 
            "    <param name=\"src\" value=\"ZoomifySlideshowViewer.swf\">\n" + 
            "    <embed flashvars=\"zoomifyXMLPath=EmbedZoomifySlideshowViewer.xml\" src=\"ZoomifySlideshowViewer.swf\" menu=\"false\" bgcolor=\"#000000\" pluginspage=\"http://www.adobe.com/go/getflashplayer\" type=\"application/x-shockwave-flash\" name=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "</object> ";
        getExtractor().extract(curi, cs);
        String expected = "http://www.example.com/ParamZoomifySlideshowViewer.xml";
        assertTrue("outlinks should contain: "+expected,
                CollectionUtils.exists(curi.getOutLinks(),destinationsIsPredicate(expected)));
    }
    
    /**
     * HER-1728 
     * @throws URIException 
     */
    public void testFlashvarsEmbedAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
        CharSequence cs = 
            "<object classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" codebase=\"http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,28,0\" id=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "    <param name=\"flashvars\" value=\"zoomifyXMLPath=ParamZoomifySlideshowViewer.xml\">\n" + 
            "    <param name=\"menu\" value=\"false\">\n" + 
            "    <param name=\"bgcolor\" value=\"#000000\">\n" + 
            "    <param name=\"src\" value=\"ZoomifySlideshowViewer.swf\">\n" + 
            "    <embed flashvars=\"zoomifyXMLPath=EmbedZoomifySlideshowViewer.xml\" src=\"ZoomifySlideshowViewer.swf\" menu=\"false\" bgcolor=\"#000000\" pluginspage=\"http://www.adobe.com/go/getflashplayer\" type=\"application/x-shockwave-flash\" name=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "</object> ";
        getExtractor().extract(curi, cs);
        String expected = "http://www.example.com/EmbedZoomifySlideshowViewer.xml";
        assertTrue("outlinks should contain: "+expected,
                CollectionUtils.exists(curi.getOutLinks(),destinationsIsPredicate(expected)));
    }
    
    /**
     * HER-1998 
     * @throws URIException 
     */
    public  void testConditionalComment1() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
    
        CharSequence cs = 
            "<!--[if IE 6]><img src=\"foo.gif\"><![endif]-->" +
            "<!--[if IE 6]><script src=\"foo.js\"><![endif]-->";
 
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        getExtractor().setLoggerModule(ulm);
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        getExtractor().setMetadata(metadata);
        getExtractor().afterPropertiesSet();
        
        getExtractor().extract(curi, cs);
        
        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links); 
        
        String dest1 = "http://www.example.com/foo.gif";
        String dest2 = "http://www.example.com/foo.js";

        assertEquals("outlink1 from conditional comment img src",dest1,
                links[0].getURI());
        assertEquals("outlink2 from conditional comment script src",dest2,
                links[1].getURI());
        
    }

    public void testImgSrcSetAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));

        CharSequence cs = "<img width=\"800\" height=\"1200\" src=\"/images/foo.jpg\" "
                + "class=\"attachment-full size-full\" alt=\"\" "
                + "srcset=\"a,b,c,,, /images/foo1.jpg 800w,data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7 700w, /images/foo2.jpg 480w(data:,foo, ,), /images/foo3.jpg 96w(x\" "
                + "sizes=\"(max-width: 800px) 100vw, 800px\">";

        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);

        String[] dest = {
                "http://www.example.com/a,b,c",
                "http://www.example.com/images/foo.jpg",
                "http://www.example.com/images/foo1.jpg",
                "http://www.example.com/images/foo2.jpg",
                "http://www.example.com/images/foo3.jpg" };
        for (int i = 0; i < links.length; i++) {
            assertEquals("outlink from img", dest[i], links[i].getURI());
        }

    }

    public void testSourceSrcSetAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));

        CharSequence cs = "<picture>"
                + "<source media=\"(min-width: 992px)\" srcset=\"images/foo1.jpg\"> "
                + "<source media=\"(min-width: 500px)\" SRCSET=\"images/foo2.jpg\"> "
                + "<source media=\"(min-width: 0px)\" srcSet=\"images/foo3-1x.jpg 1x, images/foo3-2x.jpg 2x\"> "
                + "<img src=\"images/foo.jpg\" alt=\"\"> "
                + "</picture>";

        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);

        String[] dest = {
                "http://www.example.com/images/foo.jpg",
                "http://www.example.com/images/foo1.jpg",
                "http://www.example.com/images/foo2.jpg",
                "http://www.example.com/images/foo3-1x.jpg",
                "http://www.example.com/images/foo3-2x.jpg",
        };

        for (int i = 0; i < links.length; i++) {
            assertEquals("outlink from picture", dest[i], links[i].getURI());
        }

    }

    public void testDataAttributes20Minutes() throws URIException {
        CrawlURI curi_src = new CrawlURI(UURIFactory.getInstance("https://www.20minutes.fr/"));

        CharSequence cs_src = "<img class=\"b-lazy\" width=\"120\" height=\"78\""
                + "data-src=\"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg\""
                + "sizes=\"7.5em\" alt=\"Illustration d&#039;un avocat.\"/>";

        String[] dest_src = {
                "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg"};

        genericCrawl(curi_src, cs_src, dest_src);

        CrawlURI curi_srcset = new CrawlURI(UURIFactory.getInstance("https://www.20minutes.fr/"));

        CharSequence cs_srcset = "<img class=\"b-lazy\" width=\"120\" height=\"78\""
                + "data-srcset=\"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg 120w,"
                + "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/240x156_illustration-avocat.jpg 240w\""
                + "sizes=\"7.5em\" alt=\"Illustration d&#039;un avocat.\"/>";

        String[] dest_srcset = {
                "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg",
        		"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/240x156_illustration-avocat.jpg"};

        genericCrawl(curi_srcset, cs_srcset, dest_srcset);

    }

    public void testDataAttributesTelerama() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.telerama.fr/"));

        CharSequence cs = "<img itemprop=\"image\" src=\"https://www.telerama.fr/sites/tr_master/themes/tr/images/trans.gif\" " +
		  "data-original=\"https://www.telerama.fr/sites/tr_master/files/styles/m_640x314/public/standup.jpg?itok=w1aDSzBQsc=1012e84ed57e1b1e6ea74a47ec094242\"/>";

        String[] dest = {
                "https://www.telerama.fr/sites/tr_master/files/styles/m_640x314/public/standup.jpg?itok=w1aDSzBQsc=1012e84ed57e1b1e6ea74a47ec094242",
                "https://www.telerama.fr/sites/tr_master/themes/tr/images/trans.gif"};

        genericCrawl(curi, cs, dest);

    }

    public void testDataAttributesNouvelObs() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.telerama.fr/"));

        CharSequence cs = "<source media=\"(min-width: 640px)\" data-original-set=\"http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg\"" +
        		"srcset=\"http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg\">";

        String[] dest = {
                "http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg",
                "http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg"};

        genericCrawl(curi, cs, dest);

    }

    public void testDataAttributesEuronews() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.euronews.com/"));

        CharSequence cs = "<img class=\"m-img lazyload\" src=\"/images/vector/fallback.svg\""+
    		   "data-src=\"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg\""+
    		   "data-srcset=\"https://static.euronews.com/articles/stories/04/54/38/12/100x56_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 100w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/150x84_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 150w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/300x169_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 300w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 400w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/600x338_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 600w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/750x422_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 750w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/1000x563_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 1000w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/1200x675_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 1200w\""+
    		   "data-sizes=\"(max-width: 768px) 33vw, (max-width: 1024px) 25vw, (max-width: 1280px) 17vw, 17vw\""+
    		   "title=\"La lutte contre l&#039;épidémie de Covid-19 continue en Europe et dans le monde\"/>";

        String[] dest = {
            	"https://static.euronews.com/articles/stories/04/54/38/12/1000x563_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/100x56_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/1200x675_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
                "https://static.euronews.com/articles/stories/04/54/38/12/150x84_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/300x169_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/600x338_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/750x422_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
                "https://www.euronews.com/images/vector/fallback.svg"
        };

        genericCrawl(curi, cs, dest);

    }

    public void testDataAttributesLeMonde() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.lemonde.fr/"));

        CharSequence cs = "<img data-srcset=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg 198w, "+
        			"http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg 114w\" "+
        		"data-src=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg\" "+
        		"srcset=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg 198w, "+
        			"http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg 114w\" "+
        		"src=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg\" >";

        String[] dest = {
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg"

        };

        genericCrawl(curi, cs, dest);

    }
    
    public void testDataFullSrcAttributesSlate() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.slate.fr/"));

        CharSequence cs = "<img data-full-src=\"/sites/default/files/styles/330x188/public/alexander-andrews-qjyxsc4xb84-unsplash.jpg\" "+
        			"srcset=\"/resizer/opD2fzNY__8TCVlehObW4PRYsMQ=/120x75/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 120w,  "+
        		"/resizer/xZlmD3G7rAQ1A6yHH1_YnwdL3rw=/240x150/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 240w, "+
        		"/resizer/hIvEezJm81xiHmNW4QyYKm7_y3Q=/300x187/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 300w, "+
        		"/resizer/Yq8wR5hGgk2noF6rFM3IpnLGCJI=/360x225/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 360w, "+
        		"/resizer/T_8cJnL18KoYyOlLejO-2hz2YhM=/600x375/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 600w\" >";

        String[] dest = {
                "https://www.slate.fr/resizer/T_8cJnL18KoYyOlLejO-2hz2YhM=/600x375/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.slate.fr/resizer/Yq8wR5hGgk2noF6rFM3IpnLGCJI=/360x225/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.slate.fr/resizer/hIvEezJm81xiHmNW4QyYKm7_y3Q=/300x187/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.slate.fr/resizer/opD2fzNY__8TCVlehObW4PRYsMQ=/120x75/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.slate.fr/resizer/xZlmD3G7rAQ1A6yHH1_YnwdL3rw=/240x150/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.slate.fr/sites/default/files/styles/330x188/public/alexander-andrews-qjyxsc4xb84-unsplash.jpg"
        };

        genericCrawl(curi, cs, dest);

    }

    public void testDataLazyAttributes() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.lemonde.fr/"));

        CharSequence cs = "<source class=\"js-media-live-srcset\" data-lazy-srcset=\"https://img.lemde.fr/2023/05/08/182/0/3543/2362/220/146/30/0/11bfa3f_1683552799386-2023-04-22-heg-manoeuvre-0318.JPG\" media=\"(min-width: 576px)\">\n"
        		+ "<img class=\"js-media-live\" data-lazy=\"https://img.lemde.fr/2023/05/08/0/260/2834/2834/120/120/30/0/11bfa3f_1683552799386-2023-04-22-heg-manoeuvre-0318.JPG\" alt=\"\"> \n"
        		;

        String[] dest = {
                "https://img.lemde.fr/2023/05/08/0/260/2834/2834/120/120/30/0/11bfa3f_1683552799386-2023-04-22-heg-manoeuvre-0318.JPG",
                "https://img.lemde.fr/2023/05/08/182/0/3543/2362/220/146/30/0/11bfa3f_1683552799386-2023-04-22-heg-manoeuvre-0318.JPG"
        };

        genericCrawl(curi, cs, dest);

    }

    public void testSourceSrcsetAttributes() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.lemonde.fr/"));

        CharSequence cs = "<picture>\n"
        		+ "<source srcset=\"/fr/rimage/ftw_webp_240/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 240w\" "
        		+ "data-srcset=\"/fr/rimage/ftw_webp_240/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 240w, "
        		+ "/fr/rimage/ftw_webp_288/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 288w, "
        		+ "/fr/rimage/ftw_webp_384/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 384w, "
        		+ "/fr/rimage/ftw_webp_480/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 480w, "
        		+ "/fr/rimage/ftw_webp_2304/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666 2304w\" "
        		+ "data-aspectratio=\"1.7776666666667\" type=\"image/webp\" sizes=\"1vw\">\n"
        		+ "</picture>";

        String[] dest = {
                "https://www.lemonde.fr/fr/rimage/ftw_webp_2304/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666",
                "https://www.lemonde.fr/fr/rimage/ftw_webp_240/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666",
                "https://www.lemonde.fr/fr/rimage/ftw_webp_240/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666",
                "https://www.lemonde.fr/fr/rimage/ftw_webp_288/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666",
                "https://www.lemonde.fr/fr/rimage/ftw_webp_384/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666",
                "https://www.lemonde.fr/fr/rimage/ftw_webp_480/image/10/1c386e7cddfbf205b9a6fb4ca7ce9666"
        };

        genericCrawl(curi, cs, dest);

    }

    public void testDataSrcAttributes() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.104.fr/"));

        CharSequence cs = "<img class=\"b-lazy image-large\" \n"
        		+ "src=\"data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==\"\n"
        		+ "data-src-small=\"http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c80-468ffd.jpg, "
        		+ "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c233-f551d3.jpg\" \n"
        		+ "data-src-medium=\"http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c80-af15f9.jpg, "
        		+ "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c18029b.jpg\" \n"
        		+ "data-src=\"http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c8f5db0.jpg, "
        		+ "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c84eebf.jpg\" \n"
        		+ "alt=\"\"/>";

        String[] dest = {
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c18029b.jpg",
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c233-f551d3.jpg",
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c80-468ffd.jpg",
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c80-af15f9.jpg",
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c84eebf.jpg",
                "http://www.104.fr/cache/media/visuels-generaux/bandeau-home/c8f5db0.jpg"
        };

        genericCrawl(curi, cs, dest);

    }

    public void testSrcSetAttributes() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.parisien.fr/"));

        CharSequence cs = "<div class=\"lp-image-responsive\"><img class=\"lp-image-responsive_img\" " //
        		+ "src=\"http://cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg\" alt=\"Les Insoumis LP/Arnaud Journois\" "
        		+ "srcset=\"/resizer/opD2fzNY_8TCVlehObW4PRYsMQ=/120x75/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 120w, "
        		+ "/resizer/xZlmD3G7rAQ1A6yHH1_YnwdL3rw=/240x150/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 240w, "
        		+ "/resizer/T_8cJnL18KoYyOlLejO-2hz2YhM=/600x375/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg 600w\" "
        		+ "sizes=\"(max-width: 739px) 120px, (min-width: 740px) 300px\" loading=\"lazy\" fetchpriority=\"low\"></div>";
        String[] dest = {
        		"http://cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.parisien.fr/resizer/T_8cJnL18KoYyOlLejO-2hz2YhM=/600x375/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
        		"https://www.parisien.fr/resizer/opD2fzNY_8TCVlehObW4PRYsMQ=/120x75/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg",
                "https://www.parisien.fr/resizer/xZlmD3G7rAQ1A6yHH1_YnwdL3rw=/240x150/cloudfront-eu-central-1.images.arcpublishing.com/leparisien/PSAGE45Q5NEPVI75KP4AVSZ4OE.jpg"
        };
        genericCrawl(curi, cs, dest);


    }

    public void testLinkRel() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));

        String html = "<link href='/pingback' rel='pingback'>" +
                "<link href='/style.css' rel=stylesheet>" +
                "<link rel='my stylesheet rocks' href=/style2.css>" +
                "<link rel=icon href=/icon.ico>" +
                "<link href='http://dns-prefetch.example.com/' rel=dns-prefetch>" +
                "<link href=/without-rel>" +
                "<link href=/empty-rel rel=>" +
                "<link href=/just-spaces rel='   '>" +
                "<link href=/canonical rel=canonical>" +
                "<link href=/unknown rel=unknown>";

        List<String> expectedLinks = Arrays.asList(
                "E https://www.example.org/icon.ico",
                "E https://www.example.org/style.css",
                "E https://www.example.org/style2.css",
                "L https://www.example.org/canonical",
                "L https://www.example.org/unknown"
        );

        getExtractor().extract(curi, html);
        List<String> actualLinks = new ArrayList<>();
        for (CrawlURI link: curi.getOutLinks()) {
            actualLinks.add(link.getLastHop() + " " + link.getURI());
        }
        Collections.sort(actualLinks);

        assertEquals(expectedLinks, actualLinks);
    }


    public void testDisobeyRelNofollow() throws URIException {
        String html = "<a href=/normal><a href=/nofollow rel=nofollow><a href=/both><a href=/both rel=nofollow>";
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));
        getExtractor().setObeyRelNofollow(false);
        getExtractor().extract(curi, html);
        Set<String> links = curi.getOutLinks().stream().map(CrawlURI::getURI).collect(Collectors.toSet());
        assertEquals(Set.of("https://www.example.org/both",
                "https://www.example.org/normal",
                "https://www.example.org/nofollow"), links);
    }

    public void testRelNofollow() throws URIException {
        String html = "<a href=/normal></a><a href=/nofollow rel=nofollow></a><a href=/both></a>" +
                      "<a href=/both rel=nofollow></a>" +
                      "<a href=/multi1 rel='noopener nofollow'></a>" +
                      "<a href=/multi2 rel=\"nofollow nopener\"></a>" +
                      "<a href=/multi3 rel='noopener nofollow noentry'></a>";
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));
        getExtractor().setObeyRelNofollow(true);
        getExtractor().extract(curi, html);
        Set<String> links = curi.getOutLinks().stream().map(CrawlURI::getURI).collect(Collectors.toSet());
        assertEquals(Set.of("https://www.example.org/both",
                "https://www.example.org/normal"), links);
    }

    private void genericCrawl(CrawlURI curi, CharSequence cs,String[] dest){
        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);
        assertEquals("number of links", dest.length, links.length);
        for (int i = 0; i < links.length; i++) {
            assertEquals("outlink from picture", dest[i], links[i].getURI());
        }
    }

}
