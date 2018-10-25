package org.archive.modules;

import java.io.Serializable;

/**
 * Send to rabbitMQ only uris whose discovery path matches (using java regexes) the property "include". By default this
 * field is the empty string so only seeds will be included.
 */
public class AMQPFilteredPublishProcessor extends AMQPPublishProcessor implements Serializable {

    private static final long serialVersionUID = 234L;

    String include = "";

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return super.shouldProcess(curi) && curi.getPathFromSeed().matches(include);
    }
}
