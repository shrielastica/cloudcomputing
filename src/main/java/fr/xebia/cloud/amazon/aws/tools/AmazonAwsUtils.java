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
package fr.xebia.cloud.amazon.aws.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.*;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.model.*;
import com.google.common.collect.*;
import fr.xebia.workshop.monitoring.InfrastructureCreationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class AmazonAwsUtils {


    /**
     * private constructor for utils class.
     */
    private AmazonAwsUtils() {

    }

    /**
     * <p>
     * Create EC2 instances and ensure these instances are successfully started.
     * </p>
     * <p>
     * Successfully started means they reached the
     * {@link InstanceStateName#Running} state.
     * </p>
     * <p>
     * If the startup of an instance failed (e.g.
     * "Server.InternalError: Internal error on launch"), the instance is
     * terminated and another one is launched.
     * </p>
     * <p>
     * Max retry count: 3.
     * </p>
     *
     * @param runInstancesRequest
     * @param ec2
     * @return list of "Running" created instances. List size is greater or
     *         equals to given {@link RunInstancesRequest#getMinCount()}
     */
    @Nonnull
    public static List<Instance> reliableEc2RunInstances(@Nonnull RunInstancesRequest runInstancesRequest, @Nonnull AmazonEC2 ec2) {
        int initialInstanceMinCount = runInstancesRequest.getMinCount();
        int initialInstanceMaxCount = runInstancesRequest.getMaxCount();

        try {
            int tryCount = 1;
            List<Instance> result = ec2.runInstances(runInstancesRequest).getReservation().getInstances();
            result = AmazonAwsUtils.awaitForEc2Instances(result, ec2);

            //Check for instances state
            while (result.size() < initialInstanceMinCount && tryCount < 3) {
                runInstancesRequest.setMinCount(initialInstanceMinCount - result.size());
                runInstancesRequest.setMaxCount(initialInstanceMinCount - result.size());

                List<Instance> instances = ec2.runInstances(runInstancesRequest).getReservation().getInstances();
                instances = AmazonAwsUtils.awaitForEc2Instances(instances, ec2);
                result.addAll(instances);
                tryCount++;
            }

            //Check for SSH availability
            for (Iterator<Instance> itInstance = result.iterator(); itInstance.hasNext(); ) {
                Instance instance = itInstance.next();
                try {
                    if (instance.getImageId().equals(InfrastructureCreationStep.GRAPHITE_IMAGE_ID)) {
                        awaitForSshAvailability(instance, "root");
                    } else {
                        awaitForSshAvailability(instance, "ec2-user");
                    }
                } catch (IllegalStateException e) {
                    //Not available => terminate instance
                    ec2.terminateInstances(new TerminateInstancesRequest(Lists.newArrayList(instance.getInstanceId())));
                    itInstance.remove();
                }
            }


            if (result.size() < initialInstanceMinCount) {
                throw new IllegalStateException("Failure to create " + initialInstanceMinCount + " instances, only " + result.size()
                        + " instances ("
                        + Joiner.on(",").join(Collections2.transform(result, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID))
                        + ") were started on request " + runInstancesRequest);
            }

            return result;
        } finally {
            // restore runInstancesRequest state
            runInstancesRequest.setMinCount(initialInstanceMinCount);
            runInstancesRequest.setMaxCount(initialInstanceMaxCount);
        }
    }

    public final static Predicate<Instance> PREDICATE_RUNNING_OR_PENDING_INSTANCE = new Predicate<Instance>() {
        @Override
        public boolean apply(Instance instance) {
            if (InstanceStateName.Running.toString().equals(instance.getState().getName())
                    || InstanceStateName.Pending.toString().equals(instance.getState().getName())) {
                return true;
            } else {
                return false;
            }
        }
    };

    public final static String AMI_AMZN_LINUX_EU_WEST = "ami-47cefa33";

    public final static String AMI_AMZN_LINUX_EU_WEST_2012_03 = "ami-f9231b8d";

    public static final String AMI_AMZN_LINUX_EU_WEST_2012_09 = "ami-c37474b7";


    /**
     * Load {@link AWSCredentials} from '<code>AwsCredentials.properties</code>
     * '.
     */
    @Nonnull
    public static AWSCredentials loadAwsCredentials() {
        String credentialsFilePath = "AwsCredentials.properties";
        InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(credentialsFilePath);
        Preconditions.checkState(credentialsAsStream != null, "File '" + credentialsFilePath + "' NOT found in the classpath");
        try {
            return new PropertiesCredentials(credentialsAsStream);
        } catch (IOException e) {
            throw new IllegalStateException("Exception loading '" + credentialsFilePath + "'", e);
        }
    }

    /**
     * <p>
     * Wait for the ec2 instances startup and returns a list of {@link Instance}
     * with up to date values.
     * </p>
     * <p>
     * Note: some information are missing of the {@link Instance} returned by
     * {@link AmazonEC2#describeInstances(DescribeInstancesRequest)} as long as
     * the instance is not "running" (e.g. {@link Instance#getPublicDnsName()}).
     * </p>
     *
     * @param instances
     * @return up to date instances
     */
    @Nonnull
    public static List<Instance> awaitForEc2Instances(@Nonnull Iterable<Instance> instances, @Nonnull AmazonEC2 ec2) {

        List<Instance> result = Lists.newArrayList();
        for (Instance instance : instances) {
            instance = awaitForEc2Instance(instance, ec2);
            if (instance != null) {
                result.add(instance);
            }
        }
        return result;
    }

    /**
     * <p>
     * Wait for the ec2 instance startup and returns it up to date
     * </p>
     * <p>
     * Note: some information are missing of the {@link Instance} returned by
     * {@link AmazonEC2#describeInstances(DescribeInstancesRequest)} as long as
     * the instance is not "running" (e.g. {@link Instance#getPublicDnsName()}).
     * </p>
     *
     * @param instance
     * @return up to date instances or <code>null</code> if the instance crashed
     *         at startup.
     */
    @Nullable
    public static Instance awaitForEc2Instance(@Nonnull Instance instance, @Nonnull AmazonEC2 ec2) {
        logger.trace("Wait for startup of {}: {}", instance.getInstanceId(), instance);

        try {
            // initially wait for 3 secs to prevent "InvalidInstanceID.NotFound, AWS Error Message: The instance ID 'i-2f79c967' does not exist"
            Thread.sleep(3 * 1000);
        } catch (InterruptedException e) {
            throw Throwables.propagate(e);
        }


        int counter = 0;
        while (InstanceStateName.Pending.equals(instance.getState()) || (instance.getPublicIpAddress() == null)
                || (instance.getPublicDnsName() == null)) {
            logger.trace("Wait for startup of {}: {}", instance.getInstanceId(), instance);
            try {
                // 3s because ec2 instance creation < 10 seconds
                Thread.sleep(3 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instance.getInstanceId());
            DescribeInstancesResult describeInstances = ec2.describeInstances(describeInstancesRequest);

            instance = Iterables.getOnlyElement(toEc2Instances(describeInstances.getReservations()));
            counter++;
            if (counter >= 20) {
                logger.warn("Timeout waiting for startup of {}: {}", instance);
                return instance;
            }
        }

        if (InstanceStateName.ShuttingDown.equals(instance.getState()) || InstanceStateName.Terminated.equals(instance.getState())) {
            // typically a "Server.InternalError: Internal error on launch"
            logger.warn("Terminate and skip dying instance {} (stateReason={}, stateTransitionReason={}): {}",
                    new Object[]{instance.getInstanceId(), instance.getStateReason(), instance.getStateTransitionReason(), instance});
            try {
                ec2.terminateInstances(new TerminateInstancesRequest(Lists.newArrayList(instance.getInstanceId())));
            } catch (Exception e) {
                logger.warn("Silently ignore exception terminating dying instance {}: {}", new Object[]{instance.getInstanceId(),
                        instance, e});
            }

            return null;
        }

        logger.debug("Instance {} is started: {}", instance.getInstanceId(), instance);
        return instance;
    }

    /**
     * Awaits for the given healthcheck url to return 200/OK. If the url did not
     * return 200/OK within 240 secs, an {@link IllegalStateException} is
     * raised.
     *
     * @param healthCheckUrl
     * @throws IllegalStateException if the healthCheckUrl did not return 200/OK within 240
     *                               seconds.
     */
    public static void awaitForHttpAvailability(@Nonnull String healthCheckUrl) throws IllegalStateException {

        RuntimeException cause = null;
        for (int i = 0; i < 6 * 60; i++) {
            try {
                HttpURLConnection healthCheckHttpURLConnection = (HttpURLConnection) new URL(healthCheckUrl).openConnection();

                healthCheckHttpURLConnection.setReadTimeout(1000);
                healthCheckHttpURLConnection.setConnectTimeout(1000);

                int responseCode = healthCheckHttpURLConnection.getResponseCode();
                if (HttpURLConnection.HTTP_OK == responseCode) {
                    logger.info("URL {} is available", healthCheckUrl);
                    return;
                } else {
                    logger.trace("URL {} is not yet available, responseCode={}", healthCheckUrl, responseCode);

                    cause = new IllegalStateException("'" + healthCheckUrl + "' returned response code " + responseCode);
                }
            } catch (IOException e) {
                cause = new IllegalStateException("Exception invoking '" + healthCheckUrl + "'", e);
                logger.trace("Exception invoking healthcheck URL {}", healthCheckUrl, e);
            }
            try {
                Thread.sleep(1 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }

        throw cause;
    }

    /**
     * Terminate all instances matching the given "Role" tag.
     *
     * @param role searched value of the "Role" tag
     * @param ec2
     */
    public static void terminateInstancesByRole(@Nonnull String role, @Nonnull AmazonEC2 ec2) {
        terminateInstancesByTagValue("Role", role, ec2);
    }

    /**
     * Terminate all instances matching the given "Workshop" tag.
     *
     * @param workshop searched value of the "Workshop" tag
     * @param ec2
     */
    public static void terminateInstancesByWorkshop(@Nonnull String workshop, @Nonnull AmazonEC2 ec2) {
        terminateInstancesByTagValue("Workshop", workshop, ec2);
    }


    /**
     * Terminate all instances matching the given "Role" and "TeamIdentifier" tags
     *
     * @param role           searched value of the "Role" tag
     * @param teamIdentifier searched value of the "TeamIdentifier" tag
     * @param ec2
     */
    public static void terminateInstancesByRoleAndTeam(@Nonnull String role, @Nonnull String teamIdentifier, @Nonnull AmazonEC2 ec2) {
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(getFilter("Role", role));
        filters.add(getFilter("TeamIdentifier", teamIdentifier));
        terminateInstancesByFilter(ec2, filters);
    }

    /**
     * Terminate all instances matching the given "Role" and "TeamIdentifier" tags
     *
     * @param role            searched value of the "Role" tag
     * @param teamIdentifiers searched values of the "TeamIdentifier" tag
     * @param ec2
     */
    public static void terminateInstancesByRoleAndTeam(@Nonnull String role, @Nonnull Collection<String> teamIdentifiers, @Nonnull AmazonEC2 ec2) {
        for (String teamIdentifier : teamIdentifiers) {
            terminateInstancesByRoleAndTeam(role, teamIdentifier, ec2);
        }
    }

    /**
     * Terminate all instances matching the given tag.
     *
     * @param tagName
     * @param tagValue if <code>null</code>, terminate all the instances having the
     *                 given tag defined.
     * @param ec2
     */
    public static void terminateInstancesByTagValue(@Nonnull String tagName, @Nullable String tagValue, @Nonnull AmazonEC2 ec2) {
        Filter filter = getFilter(tagName, tagValue);
        terminateInstancesByFilter(ec2, Arrays.asList(filter));
    }

    /**
     * Terminate EC2 instances matching all the filters given in parameters
     *
     * @param ec2     ec2 root object
     * @param filters all filters to be matched by the searched instances
     */
    private static void terminateInstancesByFilter(AmazonEC2 ec2, List<Filter> filters) {
        DescribeInstancesRequest describeInstancesWithRoleRequest = new DescribeInstancesRequest(). //
                withFilters(filters);
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesWithRoleRequest);

        Iterable<Instance> exstingInstances = AmazonAwsUtils.toEc2Instances(describeInstancesResult.getReservations());
        List<String> instanceIds = Lists
                .newArrayList(Iterables.transform(exstingInstances, AmazonAwsFunctions.EC2_INSTANCE_TO_INSTANCE_ID));

        if (instanceIds.isEmpty()) {
            logger.debug("No server tagged with filter '{}' to terminate", filters);
        } else {
            logger.info("Terminate servers tagged with filter '{}'", filters);

            ec2.terminateInstances(new TerminateInstancesRequest(instanceIds));
        }
    }

    /**
     * Give a filter over Tags attribute of EC2 instance with the given tag name and value
     *
     * @param tagName  tag name to be searched
     * @param tagValue value for the given tag name
     * @return EC2 Filter
     */
    private static Filter getFilter(String tagName, String tagValue) {
        Filter filter;
        if (tagValue == null) {
            filter = new Filter("tag:" + tagName);
        } else {
            filter = new Filter("tag:" + tagName, Arrays.asList(tagValue));
        }
        return filter;
    }

    /**
     * Converts a collection of {@link Reservation} into a collection of their
     * underlying
     *
     * @param reservations
     * @return
     */
    @Nonnull
    public static Iterable<Instance> toEc2Instances(@Nonnull Iterable<Reservation> reservations) {
        Iterable<List<Instance>> collectionOfListOfInstances = Iterables.transform(reservations,
                new Function<Reservation, List<Instance>>() {
                    @Override
                    public List<Instance> apply(Reservation reservation) {
                        return reservation.getInstances();
                    }
                });
        return Iterables.concat(collectionOfListOfInstances);
    }

    private static final Logger logger = LoggerFactory.getLogger(AmazonAwsUtils.class);

    /**
     * <p>
     * Wait for the database creation and returns a {@link DBInstance} with up
     * to date values.
     * </p>
     * <p>
     * Note: some information are missing of the {@link DBInstance} returned by
     * {@link AmazonRDS#describeDBInstances(DescribeDBInstancesRequest)} as long
     * as the instance is "creating" rather than "available" (e.g.
     * {@link DBInstance#getEndpoint()} that holds the ip address).
     * </p>
     *
     * @param dbInstance
     * @param rds
     * @return up to date 'available' dbInstance
     * @throws DBInstanceNotFoundException the given <code>dbInstance</code> no longer exists (e.g. it
     *                                     has been destroyed, etc).
     */
    @Nonnull
    public static DBInstance awaitForDbInstanceCreation(@Nonnull DBInstance dbInstance, @Nonnull AmazonRDS rds)
            throws DBInstanceNotFoundException {
        logger.info("Get Instance " + dbInstance.getDBInstanceIdentifier() + "/" + dbInstance.getDBName() + " status");

        while (!"available".equals(dbInstance.getDBInstanceStatus())) {
            logger.info("Instance " + dbInstance.getDBInstanceIdentifier() + "/" + dbInstance.getDBName() + " not yet available, sleep...");
            try {
                // 20 s because MySQL creation takes much more than 60s
                Thread.sleep(20 * 1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            DescribeDBInstancesRequest describeDbInstanceRequest = new DescribeDBInstancesRequest().withDBInstanceIdentifier(dbInstance
                    .getDBInstanceIdentifier());
            DescribeDBInstancesResult describeDBInstancesResult = rds.describeDBInstances(describeDbInstanceRequest);

            List<DBInstance> dbInstances = describeDBInstancesResult.getDBInstances();
            Preconditions.checkState(dbInstances.size() == 1, "Exactly 1 db instance expected : %S", dbInstances);
            dbInstance = Iterables.getFirst(dbInstances, null);

        }
        return dbInstance;
    }

    /**
     * <p>
     * Test for SSH availability on <i>instance</i>.
     * If after 5 minutes the instance is not available, and {@link IllegalStateException} is thrown
     * </p>
     * <p>
     * See <a href=
     * "http://sthen.blogspot.com/2008/03/sftp-i-java-with-jsch-using-private-key.html"
     * >SFTP in Java with JSch Using Private Key Authentication </a>
     * </p>
     * <p>
     * See {@link JSch#addIdentity(String, byte[], byte[], byte[])} with the
     * priv key as bytes.
     * </p>
     *
     * @param instance The ec2 instance to test
     * @param username
     * @see JSch#addIdentity(String, byte[], byte[], byte[])
     */
    public static void awaitForSshAvailability(Instance instance, String username) {
        try {
            final String ip = instance.getPublicIpAddress();
            final String host = instance.getPublicDnsName();

            //Read key file
            InputStream keyFile = Thread.currentThread().getContextClassLoader().getResourceAsStream(instance.getKeyName() + ".pem");
            byte[] keyAsByte = new byte[keyFile.available()];
            keyFile.read(keyAsByte);

            JSch jSch = new JSch();
            java.util.Properties config = new java.util.Properties();
            //Dont check host name
            config.put("StrictHostKeyChecking", "no");
            //Use ip instead of host to prevent dns replication latency
            Session session = jSch.getSession(username, ip);
            session.setConfig(config);

            jSch.addIdentity(username, keyAsByte, null, new byte[0]);
            for (int i = 0; i < 60; i++) {
                try {
                    session.connect(5000);
                    session.disconnect();
                    logger.info("Instance " + host + " is valid: 'ssh -i " + instance.getKeyName() + ".pem" + " ec2-user@" + instance.getPublicIpAddress() + "' SUCCESSFUL");
                    return;
                } catch (JSchException jsche) {
                    logger.debug("SSH Test - Instance not (yet) ready (" + host + ") : " + jsche.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            logger.error("Instance " + host + " is not valid !");
            throw new IllegalStateException("Instance is read after 5 minutes");
        } catch (FileNotFoundException e) {
            Throwables.propagate(e);
        } catch (IOException e) {
            Throwables.propagate(e);
        } catch (JSchException e) {
            Throwables.propagate(e);
        }
    }

    /**
     * Delete given application and wait for its removal.
     *
     * @param applicationName
     * @param beanstalk
     */
    public static void deleteBeanstalkApplicationIfExists(@Nonnull String applicationName, @Nonnull AWSElasticBeanstalk beanstalk) {
        ApplicationDescription application = Iterables.getFirst(beanstalk.describeApplications(new DescribeApplicationsRequest().withApplicationNames(applicationName)).getApplications(), null);
        if (application == null) {
            logger.debug("No application named '{}' found", applicationName);
            return;
        }

        synchronousTerminateEnvironments(applicationName, beanstalk);

        beanstalk.deleteApplication(new DeleteApplicationRequest().withApplicationName(applicationName));
        logger.info("Existing application {} deleted", applicationName);

        int counter = 0;
        while (application != null && counter < 1 * 60) {
            logger.debug("Wait for deletion of application {}", application);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
            application = Iterables.getFirst(beanstalk.describeApplications(new DescribeApplicationsRequest().withApplicationNames(applicationName)).getApplications(), null);
        }
        if (application == null) {
            logger.info("Application {} successfully deleted", applicationName);
        } else {
            logger.warn("Failure to delete application {}", applicationName);
        }
    }

    public static void synchronousTerminateEnvironments(@Nonnull String applicationName, @Nonnull AWSElasticBeanstalk beanstalk) {
        Set<String> statusToTerminate = Sets.newHashSet("Launching", "Updating", "Ready");
        Set<String> statusTerminating = Sets.newHashSet("Terminating");

        List<EnvironmentDescription> environments = beanstalk.describeEnvironments(new DescribeEnvironmentsRequest().withApplicationName(applicationName)).getEnvironments();
        List<EnvironmentDescription> environmentsToWaitFor = Collections.emptyList();

        int counter = 0;
        while (counter < 1 * 60) {
            environmentsToWaitFor = Lists.newArrayList();
            for (EnvironmentDescription environment : environments) {
                if (statusToTerminate.contains(environment.getStatus())) {
                    TerminateEnvironmentResult terminateEnvironmentResult = beanstalk.terminateEnvironment(new TerminateEnvironmentRequest().withEnvironmentId(environment.getEnvironmentId()));
                    logger.debug("Terminate environment {}, status:{} - ", new Object[]{environment.getEnvironmentName(), environment.getStatus(), terminateEnvironmentResult});
                    environmentsToWaitFor.add(environment);
                } else if (statusTerminating.contains(environment.getStatus())) {
                    environmentsToWaitFor.add(environment);
                    logger.debug("Skip termination of not running environment {}", environment);
                } else {
                    logger.trace("skip terminated environment {}", environment);
                }
            }
            if(environmentsToWaitFor .isEmpty()) {
                break;
            } else  {
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
                environments = beanstalk.describeEnvironments(new DescribeEnvironmentsRequest().withApplicationName(applicationName)).getEnvironments();
            }
        }

        if (!environmentsToWaitFor.isEmpty()) {
            logger.warn("Failure to terminate {}", environmentsToWaitFor);
        }
    }

    public static void createTags(Instance instance, CreateTagsRequest createTagsRequest, AmazonEC2 ec2) {
        // "AWS Error Code: InvalidInstanceID.NotFound, AWS Error Message: The instance ID 'i-d1638198' does not exist"
        AmazonAwsUtils.awaitForEc2Instance(instance, ec2);

        try {
            ec2.createTags(createTagsRequest);
        } catch (AmazonServiceException e) {
            // retries 5s later
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            ec2.createTags(createTagsRequest);
        }
    }

    public static void deleteCnameIfExist(Iterable<String> cnames, HostedZone hostedZone, AmazonRoute53 route53) {
        // List all
        ListResourceRecordSetsRequest listResourceRecordSetsRequest = new ListResourceRecordSetsRequest()
                // .withStartRecordType(RRType.CNAME)
                .withHostedZoneId(hostedZone.getId());
        ListResourceRecordSetsResult listResourceRecordSetsResult = route53.listResourceRecordSets(listResourceRecordSetsRequest);

        if (listResourceRecordSetsResult.isTruncated()) {
            logger.warn("truncated result");
        }

        Function<ResourceRecordSet, String> cnameExtractor = new Function<ResourceRecordSet, String>() {
            @Override
            public String apply(@Nullable ResourceRecordSet resourceRecordSet) {
                if (resourceRecordSet == null) {
                    return null;
                }
                if (!RRType.CNAME.equals(RRType.fromValue(resourceRecordSet.getType()))) {
                    return null;
                }
                return resourceRecordSet.getName();
            }
        };

        Iterable<ResourceRecordSet> existingCnamesAsResourceRecordSet = Iterables.filter(listResourceRecordSetsResult.getResourceRecordSets(), new Predicate<ResourceRecordSet>() {
            @Override
            public boolean apply(@Nullable ResourceRecordSet resourceRecordSet) {
                return RRType.CNAME.equals(RRType.fromValue(resourceRecordSet.getType()));
            }
        });

        final ImmutableMap<String, ResourceRecordSet> existingCnames = Maps.uniqueIndex(existingCnamesAsResourceRecordSet, cnameExtractor);


        Sets.SetView<String> cnamesToDelete = Sets.intersection(Sets.newHashSet(cnames), existingCnames.keySet());

        Function<String, Change> cnameToDeleteCnameChange = new Function<String, Change>() {
            @Override
            public Change apply(@Nullable String cname) {
                ResourceRecordSet existingResourceRecordSet = existingCnames.get(cname);

                return new Change()
                        .withAction(ChangeAction.DELETE)
                        .withResourceRecordSet(new ResourceRecordSet()
                                .withType(RRType.CNAME)
                                .withName(cname)
                                .withTTL(existingResourceRecordSet.getTTL())
                                .withResourceRecords(existingResourceRecordSet.getResourceRecords()));
            }
        };

        List<Change> changes = Lists.newArrayList(Iterables.transform(cnamesToDelete, cnameToDeleteCnameChange));
        if (changes.isEmpty()) {
            logger.debug("No CNAME to delete");
            return;
        }

        logger.info("Delete CNAME changes {}", changes);
        ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest = new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withChangeBatch(new ChangeBatch().withChanges(changes));
        route53.changeResourceRecordSets(changeResourceRecordSetsRequest);
    }

    public static void createCnamesForInstances(Map<String, Instance> cnameToInstances, HostedZone hostedZone, AmazonRoute53 route53) {
        Function<Map.Entry<String, Instance>, Change> cnameAndInstanceToChange = new Function<Map.Entry<String, Instance>, Change>() {
            @Override
            public Change apply(@Nullable Map.Entry<String, Instance> entry) {
                String cname = entry.getKey();
                Instance instance = entry.getValue();
                return new Change()
                        .withAction(ChangeAction.CREATE)
                        .withResourceRecordSet(new ResourceRecordSet()
                                .withType(RRType.CNAME)
                                .withName(cname)
                                .withTTL(300L)
                                .withResourceRecords(new ResourceRecord(instance.getPublicDnsName())));
            }
        };
        List<Change> changes = Lists.newArrayList(Iterables.transform(cnameToInstances.entrySet(), cnameAndInstanceToChange));

        logger.debug("Create CNAME {}", changes);

        ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest = new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZone.getId())
                .withChangeBatch(new ChangeBatch().withChanges(changes));
        route53.changeResourceRecordSets(changeResourceRecordSetsRequest);


    }


}
