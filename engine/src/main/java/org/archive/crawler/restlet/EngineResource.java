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

package org.archive.crawler.restlet;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import freemarker.template.ObjectWrapper;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.restlet.models.EngineModel;
import org.archive.crawler.restlet.models.ViewModel;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.representation.Variant;

import static org.restlet.data.MediaType.APPLICATION_XML;

/**
 * Restlet Resource representing an Engine that may be used
 * to assemble, launch, monitor, and manage crawls. 
 * 
 * @author gojomo
 * @author nlevitt
 * @author adam-miller
 */
public class EngineResource extends BaseResource {

    @Override
    public void init(Context ctx, Request req, Response res) {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(APPLICATION_XML));
    }

    @Override
    protected Representation get(Variant variant) throws ResourceException {
        if (variant.getMediaType() == APPLICATION_XML) {
            return new WriterRepresentation(APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "engine", makeDataModel());
                }
            };
        } else {
            ViewModel viewModel = new ViewModel();
            viewModel.put("fileSeparator", File.separator);
            viewModel.put("engine", makeDataModel());
            return render("Engine.ftl", viewModel, ObjectWrapper.DEFAULT_WRAPPER);
        }
    }

    @Override
    protected Representation post(Representation entity, Variant variant) throws ResourceException {
        Form form = new Form(entity);
        String action = form.getFirstValue("action");
        if("rescan".equals(action)) {
            getEngine().findJobConfigs(); 
        } else if ("add".equals(action)) {
            String path = form.getFirstValue("addpath");
            if (path==null) {
                Flash.addFlash(getResponse(), "Cannot add <i>null</i> path",
                        Flash.Kind.NACK);
            } else {
                File jobFile = new File(path);
                String jobName = jobFile.getName();
                if (!jobFile.isDirectory()) {
                    Flash.addFlash(getResponse(), "Cannot add non-directory: <i>" 
                            + path + "</i>", Flash.Kind.NACK);
                } else if (getEngine().getJobConfigs().containsKey(jobName)) {
                    Flash.addFlash(getResponse(), "Job exists: <i>" 
                            + jobName + "</i>", Flash.Kind.NACK);
                } else if (getEngine().addJobDirectory(new File(path))) {
                    Flash.addFlash(getResponse(), "Added crawl job: "
                            + "'" + path + "'", Flash.Kind.ACK);
                } else {
                    Flash.addFlash(getResponse(), "Could not add job: "
                            + "'" + path + "'", Flash.Kind.NACK);
                }
            }
        } else if ("create".equals(action)) {
        	String path = form.getFirstValue("createpath");
        	if (path==null) {
                // protect against null path
        		Flash.addFlash(getResponse(), "Cannot create <i>null</i> path.", 
        		        Flash.Kind.NACK);
        	} else if (path.indexOf(File.separatorChar) != -1) {
        	    // prevent specifying sub-directories
        		Flash.addFlash(getResponse(), "Sub-directories disallowed: "
        		        + "<i>" + path + "</i>", Flash.Kind.NACK);
        	} else if (getEngine().getJobConfigs().containsKey(path)) {
        	    // protect existing jobs
        		Flash.addFlash(getResponse(), "Job exists: <i>" + path + "</i>", 
        		        Flash.Kind.NACK);
        	} else {
        	    // try to create new job dir
        	    File newJobDir = new File(getEngine().getJobsDir(),path);
        	    if (newJobDir.exists()) {
                    // protect existing directories
                    Flash.addFlash(getResponse(), "Directory exists: "
                            + "<i>" + path + "</i>", Flash.Kind.NACK);
        	    } else {
        	        if (getEngine().createNewJobWithDefaults(newJobDir)) {
        	            Flash.addFlash(getResponse(), "Created new crawl job: "
        	                    + "<i>" + path + "</i>", Flash.Kind.ACK);
        	            getEngine().findJobConfigs();
        	        } else {
                        Flash.addFlash(getResponse(), "Failed to create new job: "
                                + "<i>" + path + "</i>", Flash.Kind.NACK);
        	        }
        	    }
        	}
        } else if ("exit java process".equals(action)) {
            System.out.println("processing java process exit request.");
            boolean cancel = false; 
            if(!"on".equals(form.getFirstValue("im_sure"))) {
                Flash.addFlash(
                        getResponse(),
                        "You must tick \"I'm sure\" to trigger exit", 
                        Flash.Kind.NACK);
                System.out.println("Rejecting exit request (1).");
                cancel = true;
            }
            for(Map.Entry<String,CrawlJob> entry : getBuiltJobs().entrySet()) {
                if(!"on".equals(form.getFirstValue("ignore__"+entry.getKey()))) {
                    Flash.addFlash(
                            getResponse(),
                            "Job '"+entry.getKey()+"' still &laquo;"
                                +entry.getValue().getJobStatusDescription()
                                +"&raquo;", 
                            Flash.Kind.NACK);
                    System.out.println("Rejecting exit request (1).");
                    cancel = true; 
                }
            }
            if(!cancel) {
                System.out.println("Exiting now.");
                System.exit(0); 
            }
        } else if ("gc".equals(action)) {
            System.gc();
        }
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
        return new EmptyRepresentation();
    }
    
 
    protected HashMap<String, CrawlJob> getBuiltJobs() {
        HashMap<String,CrawlJob> builtJobs = new HashMap<String,CrawlJob>(); 
        for(Map.Entry<String,CrawlJob> entry : getEngine().getJobConfigs().entrySet()) {
            if(entry.getValue().hasApplicationContext()) {
                builtJobs.put(entry.getKey(),entry.getValue());
            }
        }
        return builtJobs;
    }


    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected EngineModel makeDataModel() {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        
        return new EngineModel(getEngine(), baseRef);
    }
}
