#summary Continuous Delivery with Jenkins and rundeck Lab for team '${infrastructure.identifier}'

*<font size="5">Continuous Delivery with Rundeck Lab for Team '${infrastructure.identifier}'</font>*


= Your architecture =

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/per-team-infrastructure.png" />

<table>
<tr><td> *SSH Private key* </td><td> [https://s3-eu-west-1.amazonaws.com/continuous-delivery/continuous-delivery-workshop.pem continuous-delivery-workshop.pem]</td></tr>
<tr><td> *!GitHub project repository url* </td><td> [${infrastructure.githubRepositoryHomePageUrl}] </td></tr>
<tr><td> *Jenkins URL* </td><td> [${infrastructure.jenkinsUrl}] </td></tr>
<tr><td> *Rundeck URL* </td><td> [${infrastructure.rundeckUrl}] </td></tr>
<tr><td> *Deployit URL* </td><td> [${infrastructure.deployitUrl}] </td></tr>
<tr><td> *Nexus URL* </td><td> [${infrastructure.nexusUrl}] </td></tr>
<tr><td> *Tomcat Dev URL* </td><td> [${infrastructure.devTomcatUrl}] </td></tr>
<tr><td> *Tomcat Dev SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.devTomcat.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 1 URL* </td><td> [${infrastructure.validTomcat1Url}] </td></tr>
<tr><td> *Tomcat Valid 1 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat1.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 2 URL* </td><td> [${infrastructure.validTomcat2Url}] </td></tr>
<tr><td> *Tomcat Valid 2 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat2.publicDnsName}
}}}
 </td></tr>
</table>

----

= Lab. Automated Tomcat Deployment with Rundeck = 

*Goal:* Use [http://rundeck.org/ Rundeck] to execute tasks on remote servers => deploy the petclinic application on both tomcat-valid servers using the previous Shell script.
We will also use the "Jenkins Rundeck Plugin" to automatically deploy the application after a successful build.

== Architecture ==

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-rundeck.png"/>

== Lab ==

=== Deploy Petclinic from Rundeck ===
Here is the configuration of the previous task with Rundeck.
 # Make sure the "[http://xebia-france.googlecode.com/svn/cloudcomputing/xebia-cloudcomputing-extras/trunk/src/main/scripts/fr/xebia/workshop/continuousdelivery/tomcat-deploy-petclinic tomcat-deploy-petclinic]" shell script has been uploaded on your "Tomcat Valid 1" and "Tomcat Valid 2" servers (see the 'Scripted Tomcat Deployment' section)
 # Connect to your Rundeck server ${infrastructure.rundeckUrl} (login=admin, password=admin)
 # A project has already been created : "{{{deploy-petclinic-on-tomcat-valid}}}", but it has no knowledge of your Tomcat-valid servers...
 # We will configure the rundeck project : connect via SSH to the rundeck server: 
  {{{
ssh -i ~/.aws/continuous-delivery-workshop.pem ec2-user@${infrastructure.rundeck.publicDnsName}
}}}
  # Edit the file *{{{/var/rundeck/projects/deploy-petclinic-on-tomcat-valid/etc/resources.xml}}}* in order to add your valid tomcat servers :
    <code language="xml"><node 
	name="valid-tomcat-1" 
	description="valid-tomcat-1" 
	tags="tomcat,valid" 
	hostname="${infrastructure.validTomcat1.publicDnsName}" 	
	osArch="i386" 
	osFamily="unix" 
	osName="Linux" 
	osVersion="" 
	username="tomcat" 
	privateIpAddress="${infrastructure.validTomcat1.privateIpAddress}" 	
	privateDnsName="${infrastructure.validTomcat1.privateDnsName}"
	editUrl="https://console.aws.amazon.com/ec2/home?region=eu-west-1"/>
<node 
	name="valid-tomcat-2" 
	description="valid-tomcat-2" 
	tags="tomcat,valid" 
	hostname="${infrastructure.validTomcat2.publicDnsName}" 	
	osArch="i386" 
	osFamily="unix" 
	osName="Linux" 
	osVersion="" 
	username="tomcat" 
	privateIpAddress="${infrastructure.validTomcat2.privateIpAddress}" 	
	privateDnsName="${infrastructure.validTomcat2.privateDnsName}"
	editUrl="https://console.aws.amazon.com/ec2/home?region=eu-west-1"/>
</code>
 # Go back to [${infrastructure.rundeckUrl} Rundeck web admin console], and let's have a look at all your nodes (servers) :
 # Click on the filter ("Name: localhost   Project: deploy-petclinic-on-tomcat-valid") and in the "name" filter, replace "localhost" with "{{{.*}}}" and click on "Filter". You should now see all 3 servers : the 2 tomcat servers, and the local rundeck server.
 # You can test that everything works by executing a command on all 3 servers : type "whoami" in the command field, and click "Run"...
 # Now, we will create our first job : click on the "Jobs" tab on the top of the page, and then on "New job ..." (on the right)
  <img height="100" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/rundeck-new-job-screenshot.png" />
 # Create a new job:
  * Save this Job?: {{{Yes}}}
  * Name: {{{tomcat-deploy-petclinic}}}
  * Options:
   ** Name: {{{version}}}
   ** Default value: {{{LATEST}}}
   ** Remote URL:
     {{{
  http://nexus.aws.xebia-tech-event.info:8081/nexus/service/local/rundeck/options/version?g=${infrastructure.projectMavenGroupId}&a=xebia-petclinic-lite&includeLatest=true
}}}
   ** Restrictions: {{{Enforced from Allowed Values}}}
   ** Click on the "save" button to save this option
  * Shell command: 
     {{{
  /opt/tomcat/apache-tomcat-6/bin/tomcat-deploy-petclinic fr.xebia.demo.petclinic-${infrastructure.identifier} ${r"${option.version}"}
}}}
   ** Click on the "save" button to save this command
  * Dispatch to Node: {{{checked}}}
  * Includes: {{{tags}}}, type "{{{tomcat}}}", and check that the "matched nodes" are valid-tomcat-1 and valid-tomcat-2...
  * Thread Count: {{{2}}} (so that your application is deployed on the 2 servers at the same time)
  * Click on *Create and Run*
   <img height="350" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/rundeck-new-job-details-screenshot.png" />
  * While the script is running, follow the output...
  * At the end, check your apps on both tomcat servers : [${infrastructure.validTomcat1Url}xebia-petclinic-lite/] and [${infrastructure.validTomcat2Url}xebia-petclinic-lite/]

=== Nexus integration (multiple versions) ===

When you execute a job in Rundeck, you can choose the version of the WAR to deploy. The list of available versions comes from Nexus.
If you have only 1 version in the list, it is the moment to do a release of the petclinic application : run {{{mvn release:prepare release:perform}}} and re-execute the Rundeck job : you can now choose the version to deploy !
 
=== Invoke Rundeck job from Jenkins ===

We will now link Jenkins and Rundeck, so that each successfull build is followed by a deployment on the valid servers :

 * Go to the Jenkins configuration page [${infrastructure.jenkinsUrl}/configure], and verify the pre-configured Rundeck connection parameters (in the bottom of the page) :
  ** URL: {{{http://${infrastructure.rundeck.publicDnsName}:4440/}}}
  ** Login: {{{admin}}}
  ** Password: {{{admin}}}
  ** Click on "Test connection", you should get a success message
  ** Save
  <img height="75" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-rundeck-connection-screenshot.png" />

 * Edit your Jenkins job at [${infrastructure.jenkinsUrl}/job/${infrastructure.githubRepositoryName}/configure], and configure the Rundeck plugin (in the bottom of the page) with the following parameters :
  ** Rundeck job identifier: {{{deploy-petclinic-on-tomcat-valid:tomcat-deploy-petclinic}}}
  ** Job options (optional): {{{version = LATEST}}}
  ** Should fail the build ?: {{{checked}}}
  <img height="140" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-job-rundeck-configuration-screenshot.png" />
 * Save, trigger the build and verify it works

You can now test your deployment chain by editing a file in the petclinic project, and committing/pushing it to github : Jenkins will pick up the change, build the WAR, deploy it to Nexus, and ask Rundeck to deploy the project on the tomcat valid servers.

You can also do "on-demand" deploys : in the Jenkins job configuration page at [${infrastructure.jenkinsUrl}/job/${infrastructure.githubRepositoryName}/configure], in the Rundeck plugin parameters, type "#deploy" in the "tag" field. Save and trigger a build : it won't be deploy on rundeck. But if you commit a file with a commit message containing "#deploy", it will be deployed. 

----
_${generator}_
