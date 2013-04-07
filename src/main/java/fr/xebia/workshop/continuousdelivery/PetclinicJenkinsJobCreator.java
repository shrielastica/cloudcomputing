/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.workshop.continuousdelivery;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.xebia.cloud.cloudinit.FreemarkerUtils;

/**
 * Creates a job in a Jenkins server for a "Petclinic" project hosted on Github.
 * 
 * Example:
 * 
 * <pre>
 * new PetclinicJenkinsJobCreator(&quot;http://ec2-46-137-62-232.eu-west-1.compute.amazonaws.com:8080&quot;).create(new PetclinicProjectInstance(&quot;xebia-guest&quot;, &quot;42&quot;))
 *         .triggerBuild();
 * </pre>
 */
public class PetclinicJenkinsJobCreator {

    private static final Logger logger = LoggerFactory.getLogger(PetclinicJenkinsJobCreator.class);

    private final String jenkinsUrl;

    public PetclinicJenkinsJobCreator(@Nonnull String jenkinsUrl) {
        this.jenkinsUrl = checkNotNull(jenkinsUrl);
        checkArgument(jenkinsUrl.startsWith("http://"), "Invalid URL provided for Jenkins server: " + jenkinsUrl);
    }

    public PostCreationActions create(@Nonnull PetclinicJobInstance project) {
        checkNotNull(project);

        HttpPost post = new HttpPost(jenkinsUrl + "/createItem?name=" + project.getProjectName());
        Map<String, Object> parameters = newHashMap();
        parameters.put("githubAccountName", project.getGithubAccountName());
        parameters.put("projectName", project.getProjectName());
        parameters.put("groupId", project.getGroupId());
        parameters.put("artifactId", project.getArtifactId());
        String jobConfig = FreemarkerUtils.generate(parameters, "/fr/xebia/workshop/continuousdelivery/petclinic-jenkins-job-config.xml.ftl");

        HttpEntity httpEntity;
        try {
            httpEntity = new StringEntity(jobConfig, "text/xml", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported UTF-8 should never occur", e);
        }
        post.setEntity(httpEntity);

        logger.info("Creating job {}: {}", project.getProjectName(), post.getURI());
        new Client().post(post);
        return new PostCreationActions(jenkinsUrl, project);
    }

    public static class JobCreationException extends RuntimeException {

        private static final long serialVersionUID = -559471701873832122L;

        public JobCreationException(String message) {
            super(message);
        }

        public JobCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class Client {

        void post(HttpPost post) {
            HttpClient client = new DefaultHttpClient();
            try {
                logger.debug("Executing request {}", post.getRequestLine());

                HttpResponse response;
                try {
                    response = client.execute(post);
                } catch (Exception e) {
                    throw new JobCreationException("Could not execute request", e);
                }

                logger.debug("Response status: {}", response.getStatusLine());

                if (!newHashSet(HttpStatus.SC_OK, HttpStatus.SC_MOVED_TEMPORARILY).contains(response.getStatusLine().getStatusCode())) {
                    try {
                        logger.warn(EntityUtils.toString(response.getEntity()));
                    } catch (Exception e) {
                        logger.warn("Could not print entity");
                    }
                    throw new JobCreationException(response.getStatusLine().toString());
                }
            } finally {
                client.getConnectionManager().shutdown();
            }
        }
    }

    public static class PostCreationActions {

        private final String jenkinsUrl;
        private final PetclinicJobInstance project;

        public PostCreationActions(String jenkinsUrl, PetclinicJobInstance project) {
            this.jenkinsUrl = jenkinsUrl;
            this.project = project;
        }

        /**
         * Triggers a build so that dependencies are loaded.
         */
        public void triggerBuild() {
            logger.info("Trigger build of {}", project.getProjectName());
            new Client().post(new HttpPost(String.format("%s/job/%s/build", jenkinsUrl, project.getProjectName())));
        }
    }
}
