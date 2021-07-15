package org.archive.modules.extractor;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import crawlercommons.mimetypes.MimeTypeDetector;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.ContentExtractor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;

import crawlercommons.sitemaps.AbstractSiteMap;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapIndex;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import crawlercommons.sitemaps.UnknownFormatException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 *     TODO @author Colin Rosenthal <csr@kb.dk>
 *     There are two functional changes added by KB-Denmark
 *     i) shouldExtract() is made much more lenient with sitemap identification for .gz files
 *     ii) the call to siteMapParser is made lenient with respect to URLs
 *
 *     We should either
 *     i) Make this a new class ExtractorSitemapLenient, or
 *     ii) Make these configurable fields in the bean
 *
 *
 */
public class ExtractorSitemap extends ContentExtractor {
    private static final Logger LOGGER = Logger
            .getLogger(ExtractorSitemap.class.getName());

    /* (non-Javadoc)
     * @see org.archive.modules.extractor.ContentExtractor#shouldExtract(org.archive.modules.CrawlURI)
     */
    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        // If declared as such:
        if (uri.getAnnotations()
                .contains(ExtractorRobotsTxt.ANNOTATION_IS_SITEMAP)) {
            if (uri.is2XXSuccess()) {
                LOGGER.fine("This url (" + uri
                        + ") is declared to be a sitemap (via robots.txt) and is a HTTP 200.");
                return true;
            } else {
                LOGGER.fine("This url (" + uri
                        + ") is declared to be a sitemap (via robots.txt) but is a HTTP "
                        + uri.getFetchStatus() + ".");
            }
        }

        // Via content type:
        String mimeType = uri.getContentType();
        if (mimeType != null ) {
            // Looks like XML:
            if (mimeType.toLowerCase().startsWith("text/xml")
                    || mimeType.toLowerCase().startsWith("application/xml")  || uri.getURI().contains(".xml")) {
                //System.out.println("Sniffing " + uri.getBaseURI() + " for sitemap info.");
                if (uri.getURI().contains("sitemap") && uri.getURI().contains(".xml.gz")) {
                    /*System.out.println("Based on uri, this is a sitemap: "
                            + uri);*/
                    return true;
                }
                // check if content starts with xml preamble "<?xml" and does
                // contain "<urlset " or "<sitemapindex" early in the content
                String contentStartingChunk = uri.getRecorder()
                        .getContentReplayPrefixString(400);
                if (contentStartingChunk.matches("(?is)[\\ufeff]?<\\?xml\\s.*")
                        && contentStartingChunk.matches(
                                "(?is).*(?:<urlset|<sitemapindex[>\\s]).*")) {
                    LOGGER.info("Based on content sniffing, this is a sitemap: "
                            + uri);
                    /*System.out.println("Based on content sniffing, this is a sitemap: "
                            + uri);*/
                    return true;
                } else {
                   /* System.out.println("Based on content sniffing, this is not a sitemap: "
                            + uri);*/
                }

            }
        }
        
        // Otherwise, not
        return false;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.extractor.ContentExtractor#innerExtract(org.archive.modules.CrawlURI)
     */
    @Override
    protected boolean innerExtract(CrawlURI uri) {
        //System.out.println("Extracting possible sitemap " + uri.getURI());
        // Parse the sitemap:
        AbstractSiteMap sitemap = parseSiteMap(uri);

        // Did that work?
        if (sitemap != null) {
            //System.out.println("Extracted sitemap " + uri.getURI());
            // Process results:
            if (sitemap.isIndex()) {
                final Collection<AbstractSiteMap> links = ((SiteMapIndex) sitemap)
                        .getSitemaps();
                //System.out.println("Found index sitemap " + uri.getURI() + " with " +
                //links.size() + " links.");
                for (final AbstractSiteMap asm : links) {
                    if (asm == null) {
                        continue;
                    }
                    this.recordOutlink(uri, asm.getUrl(), asm.getLastModified(),
                            true);
                }
            } else {
                final Collection<SiteMapURL> links = ((SiteMap) sitemap)
                        .getSiteMapUrls();
                /*System.out.println("Found non-index sitemap " + uri.getURI() + " with " +
                        links.size() + " links.");*/
                for (final SiteMapURL url : links) {
                    if (url == null) {
                        continue;
                    }
                    this.recordOutlink(uri, url.getUrl(), url.getLastModified(),
                            false);
                }
            }
        }

        return false;
    }

    /**
     * Parse the sitemap using the Crawler Commons content-sniffing parser.
     * 
     * @param uri
     * @return
     */
    private AbstractSiteMap parseSiteMap(CrawlURI uri) {
        // The thing we will create:
        AbstractSiteMap sitemap = null;

        // Be lenient about URLs and allow partial extraction:
        SiteMapParser smp = new SiteMapParser(false, true);
        // Parse it up:
        try {
            // Sitemaps are not supposed to be bigger than 50MB (according to
            // Google) so if we hit problems we can implement that limit:
            byte[] content = IOUtils.toByteArray(
                    uri.getRecorder().getContentReplayInputStream());
            if (content.length > 52428800) {
                LOGGER.warning("Found sitemap exceeding 50MB " + uri + " "
                        + content.length);
            }
            // Now we can process it:
            sitemap = smp.parseSiteMap(content, new URL(uri.getURI()));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "I/O Exception when parsing sitemap " + uri, e);
           /* System.out.println(e.getMessage());
            e.printStackTrace();*/
        } catch (UnknownFormatException e) {
            LOGGER.log(Level.WARNING,
                    "UnknownFormatException when parsing sitemap " + uri, e);
            /*System.out.println(e.getMessage());
            e.printStackTrace();*/
        }
        return sitemap;
    }

    private void recordOutlink(CrawlURI curi, URL newUri, Date lastModified,
            boolean isSitemap) {
        try {
            // Get the max outlinks (needed by add method):
            //
            // Because sitemaps are really important we excuse this extractor
            // from the general setting:
            //
            // getExtractorParameters().getMaxOutlinks();
            //
            // And instead use the maximum that is allowed for a sitemap:
            int max = 50000;

            // Add the URI:
        	// Adding 'regular' URL listed in the sitemap
            addRelativeToBase(curi, max, newUri.toString(),
                    LinkContext.MANIFEST_MISC, Hop.MANIFEST);

            // And log about it:
            LOGGER.fine("Found " + newUri + " from " + curi + " Dated "
                    + lastModified + " and with isSitemap = " + isSitemap);
            // Count it:
            numberOfLinksExtracted.incrementAndGet();
        } catch (URIException e) {
            LOGGER.log(Level.WARNING,
                    "URIException when recording outlink " + newUri, e);
        }

    }

}
